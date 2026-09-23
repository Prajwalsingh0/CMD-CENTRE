# AI Command Center

A full-stack personal AI workspace: goals, tasks, documents, a retrieval-augmented knowledge
base, job-description intelligence, a research agent, an AI activity trail and analytics — all
behind one authenticated dashboard with a global natural-language **command bar**.

The whole product runs with **zero credentials and zero external services**. `git clone`,
start it, register, and every feature works. A real LLM provider can be switched on with two
environment variables when you want it.

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Screenshots](#2-screenshots)
3. [Features](#3-features)
4. [Architecture](#4-architecture)
5. [Tech stack](#5-tech-stack)
6. [Database architecture](#6-database-architecture)
7. [AI architecture](#7-ai-architecture)
8. [API overview](#8-api-overview)
9. [Setup instructions](#9-setup-instructions)
10. [Environment variables](#10-environment-variables)
11. [Running locally](#11-running-locally)
12. [Testing](#12-testing)
13. [Docker instructions](#13-docker-instructions)
14. [Security considerations](#14-security-considerations)
15. [Future improvements](#15-future-improvements)
16. [Project structure](#16-project-structure)

---

## 1. Project overview

Most "AI side projects" are a chat box with a database behind it. This one is the opposite: a
normal, well-behaved layered application that an AI can *operate* — under constraints.

The interesting engineering problems it solves:

* **How do you let a language model change application state without letting it near the
  database?** The model chooses one of eleven whitelisted intents and fills in a flat map of
  primitive strings. A registry resolves the intent to a tool, the tool declares which argument
  names it accepts, and every service call is scoped to the authenticated user. Anything the
  model invents — an unknown intent, an undeclared argument, a non-ISO date — is rejected before
  a service is touched, and the rejection is written to an activity trail.
* **How do you ship an AI feature that a reviewer can actually run?** The default provider is
  deterministic and offline. Summarisation, keyword extraction, note generation, command parsing
  and embeddings are real algorithms rather than stubs, so the product is fully functional and
  fully testable with no API key.
* **How do you avoid inventing user qualifications?** Job analysis extracts *demand* from a job
  description and compares it against the *only* source of truth about the user — their own skill
  profile. A skill is `KNOWN`, `UNVERIFIED` or `MISSING`, and never anything else.

---

## 2. Screenshots

No binary screenshots are committed, so the repository stays free of opaque assets that go stale.
Every screen is reproducible in about a minute:

```bash
docker compose up --build      # then open http://localhost:5173 and register
```

| Route | What you will see |
|---|---|
| `/dashboard` | Stat cards, today's tasks, overdue list, upcoming deadlines, active goals, recent documents, recent AI activity, quick actions |
| `/tasks` | Filterable, sortable, paginated task list with inline status changes |
| `/goals` | Goals with progress bars derived from their tasks |
| `/documents` | Drag-and-drop upload with processing status and per-document insights |
| `/knowledge` | Ask a question, get an answer plus the exact passages it came from |
| `/jobs` | Match score and a ✓ / △ / ✗ skill-gap table against your declared skills |
| `/research` | Structured reports with an explicit grounding disclosure |
| `/activity` | Every AI action with its status and result |
| `/analytics` | Completion series, AI activity series, status and priority breakdown, goal progress |

---

## 3. Features

### Authentication and account
* Register, login, stateless JWT access tokens, BCrypt password hashing.
* Profile and a declared skill list (with a `verified` flag) that drives job analysis.
* Password hashes are never mapped into a response DTO — not by convention, by omission.

### Command centre dashboard
* One aggregate endpoint (`GET /api/dashboard`) so every panel renders from the same instant.
* Today's tasks, overdue tasks, the next seven days, active goals, upcoming deadlines, recent
  documents, recent AI activity, recent job analyses, unread notifications and productivity stats.

### AI command bar (signature feature)
* Natural language → whitelisted intent → validated arguments → existing service → database.
* Eleven intents: create a task, create a goal, create a goal **with a dated multi-day plan**,
  list tasks, list overdue tasks, change a task's status, summarise a document, ask the knowledge
  base, analyse a job description, research a topic, and report statistics.
* Every attempt — successful, rejected or failed — is recorded with the command text, the chosen
  intent, the outcome and a one-line result summary.
* Also doubles as knowledge-aware chat: a message is first retrieved against your own documents,
  and the answer carries its citations.

### Goals and tasks
* Full CRUD, statuses `TODO / IN_PROGRESS / COMPLETED / CANCELLED`, four priority levels, due
  dates, tags and optional goal association.
* Filtering by status, priority and goal; full-text search across title, description and tags;
  whitelisted sorting; bounded pagination.
* Goal progress is *derived* — cancelled tasks leave the denominator, so a goal can legitimately
  reach 100%.
* Deleting a goal detaches its tasks instead of cascading, because silently destroying work is
  never the right default.

### Document intelligence
* PDF, TXT and Markdown upload up to 8 MB, validated by extension, declared content type,
  **magic bytes** and size before a byte is written.
* Text extraction (PDFBox for PDF, strict UTF-8 for text), paragraph-aware chunking with overlap,
  embedding and indexing in one pass.
* Per-document insights: summary, key points, notes, and generated questions.

### RAG knowledge base
* Chunk → embed → store → cosine retrieval → context assembly → answer **with its sources**.
* Retrieval is always filtered by owner before ranking.
* When nothing is retrieved the API says so rather than letting a model improvise.
* `POST /api/knowledge/reindex` re-embeds the corpus, which is how you move between providers
  with different vector dimensions.

### Job intelligence
* Parses title, company, required skills, preferred skills, technologies, experience, education
  and responsibilities out of a pasted description.
* Skill-gap table with `KNOWN` / `UNVERIFIED` / `MISSING`, a weighted match score, gap analysis,
  interview topics, a day-by-day learning plan and interview questions.

### Research agent
* Topic → retrieval over your own documents → structured report: overview, key findings,
  concepts, recommendations, sources, summary.
* Never fabricates citations. Every report carries a disclosure of what it is actually based on.

### Notifications, activity and analytics
* Notifications are derived from real rows with a deterministic dedupe key, so refreshing is
  idempotent and nothing is invented.
* Analytics: totals, completion rate, overdue count, seven-day series, status and priority
  breakdown, per-goal progress — all computed from the caller's own rows.

---

## 4. Architecture

Full detail, including the reasoning behind each decision and the changes that are deliberately
frozen, is in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

```
React + TypeScript (Vite)
        │  JWT bearer
        ▼
Controller  ← DTOs + Bean Validation, no business logic
        ▼
Service     ← ownership checks, transactions, business rules
        ├──────────────► AiService (local | openai)
        ▼
Repository  ← every query scoped by user id
        ▼
PostgreSQL (prod) / H2 (dev + tests), schema owned by Flyway
```

Hard rules enforced across the codebase:

1. Controllers return DTOs, never entities.
2. Every repository call is scoped by `userId`; "not yours" is a **404**, never a 403, so the API
   cannot be used to discover other users' rows.
3. Transactions live on service methods.
4. Cross-module access goes through the other module's service.
5. All failures funnel through one handler into one error envelope.

---

## 5. Tech stack

**Backend** — Java 21, Spring Boot 3.4, Spring Web, Spring Data JPA, Spring Security (stateless
JWT via `jjwt`), Bean Validation, Flyway, Maven, PDFBox, Lombok.

**Databases** — PostgreSQL in production; H2 in `MODE=PostgreSQL` for development and tests, so
the *same* migration is proven on a real server before it ever reaches production.

**Frontend** — React 18, TypeScript (strict), Vite, React Router. No UI kit, no state-management
library, no charting library: one typed `fetch` client and hand-built SVG charts keep the
dependency graph auditable.

**Testing** — JUnit 5, Mockito, MockMvc, AssertJ, H2; Vitest and Testing Library on the frontend.

**Infrastructure** — Docker and docker-compose only. No Redis, no message broker, no WebSockets:
nothing in the feature set needs them.

---

## 6. Database architecture

Nine tables, created by `backend/src/main/resources/db/migration/V1__init_schema.sql`:

```
users ─1───n goals ─1───n tasks            (tasks.goal_id nullable, ON DELETE SET NULL)
  │
  ├─1───n user_skills                      the only source of "the user knows this"
  ├─1───n documents ─1───n document_chunks (chunk.embedding = JSON vector)
  ├─1───n job_analyses
  ├─1───n research_reports
  ├─1───n ai_activities
  └─1───n notifications                    UNIQUE (user_id, dedupe_key)
```

Two decisions worth explaining:

* **`tasks.goal_id` is a plain foreign key, not a JPA association.** The API never serialises a
  graph, and managed-entity cascades are the most common cause of accidental cross-user writes.
  Ownership is re-checked by id on every access instead.
* **Small ordered collections are stored as JSON text** (`tags`, skill lists, embedding vectors)
  via a `JsonLists` helper. The schema stays byte-identical on H2 and PostgreSQL, with no
  dialect-specific column types to maintain.

`ddl-auto: validate` means Hibernate refuses to start if an entity and the migration disagree.
Schema drift cannot survive a boot.

---

## 7. AI architecture

### Provider abstraction

```java
public interface AiService {
    String providerName();
    boolean isRemote();
    boolean supportsStructuredOutput();
    int embeddingDimensions();
    AiCompletion complete(AiRequest request);
    List<double[]> embed(List<String> texts);
}
```

* **`LocalAiService`** (default) — deterministic and offline. Extractive summarisation by
  keyword-weighted sentence scoring, keyword extraction, note and question generation, and a
  hashed bag-of-words embedding with bigram features. `supportsStructuredOutput()` is
  deliberately `false`, which is what routes command parsing to the deterministic parser and
  makes the "AI can fail" path real rather than theoretical.
* **`OpenAiService`** — any OpenAI-compatible `/chat/completions` + `/embeddings` endpoint, hand
  rolled on `RestClient` with hard timeouts. The key is sent as a header, never logged, never
  echoed into an exception, never returned in a response.

`AiConfig` is the only place that knows which implementation is live, and it fails the *startup*
— not some later request — if `AI_PROVIDER=openai` is set without a key.

### Command pipeline

```
POST /api/ai/command
   │
   ▼  AiCommandService (records an activity row in every case)
AiCommandParser  ──LLM JSON mode──►  validated against the intent enum
   │  (falls back on any failure)
   ▼  HeuristicCommandParser (deterministic patterns)
ParsedCommand(intent, arguments, rationale, source)
   │
   ▼  CommandToolRegistry  ← the whitelist; UNKNOWN has no tool and never will
CommandTool  ← validates its own declared arguments
   │
   ▼  TaskService / GoalService / DocumentService / RagService / JobService / ResearchService
   │
   ▼  Database
```

The model's entire influence over the system is one enum constant plus a flat map of strings.
There is no code path from model output to SQL, to an entity, or to a repository.

### RAG

```
upload → validate (extension, content type, magic bytes, size, traversal)
       → store at <root>/<userId>/<uuid>.<ext>
       → extract → chunk (~900 chars, 150 overlap, paragraph aware)
       → embed → persist → READY

ask    → embed question → cosine top-k over the caller's chunks
       → assemble numbered context with document names
       → complete → answer + cited sources (or an explicit "not found")
```

Embeddings are stored as JSON and scanned in memory. For a personal knowledge base that is
sub-millisecond and adds no infrastructure; the `VectorStore` interface keeps a `pgvector` or
external implementation a drop-in replacement if the corpus ever outgrows it.

---

## 8. API overview

The complete endpoint table, conventions and error envelope are in **[docs/API.md](docs/API.md)**.

```text
POST   /api/auth/register            POST   /api/auth/login           GET  /api/auth/me
GET    /api/users/me                 PUT    /api/users/me             GET  /api/users/me/skills
GET    /api/goals                    POST   /api/goals
GET    /api/tasks                    POST   /api/tasks               PATCH /api/tasks/{id}/status
GET    /api/documents                POST   /api/documents           POST /api/documents/{id}/insights
POST   /api/knowledge/ask            POST   /api/knowledge/reindex
POST   /api/jobs/analyze             GET    /api/jobs                POST /api/jobs/{id}/prep
GET    /api/research                 POST   /api/research
POST   /api/ai/command               POST   /api/ai/chat             GET  /api/ai/activity
GET    /api/dashboard                GET    /api/analytics
GET    /api/notifications            POST   /api/notifications/refresh
GET    /api/health
```

Every error uses one shape:

```json
{
  "timestamp": "2026-01-31T09:12:44.221Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Task not found: 91",
  "path": "/api/tasks/91",
  "details": []
}
```

---

## 9. Setup instructions

**Prerequisites:** JDK 21+ (the build targets 21), Maven 3.9+, Node 20+. Nothing else — no
database server is required for local development, because the `dev` profile uses in-memory H2.

```bash
git clone https://github.com/Prajwalsingh0/CMD-CENTRE.git
cd CMD-CENTRE
cp .env.example .env          # optional; every value has a safe development default
```

---

## 10. Environment variables

Every environment-derived value has a `${ENV:default}` placeholder and a development-only
default. See [.env.example](.env.example) for the annotated list.

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | required outside dev | HS256 signing key. There is **no** usable default: the `dev` profile supplies a local-only key, and any other profile fails to start if the variable is missing or shorter than 32 bytes. |
| `AI_PROVIDER` | `local` | `local` (offline, deterministic) or `openai`. |
| `AI_API_KEY` | empty | Required only when `AI_PROVIDER=openai`; startup fails without it. |
| `AI_BASE_URL` | `https://api.openai.com/v1` | Any OpenAI-compatible base URL. |
| `AI_MODEL` | `gpt-4o-mini` | Chat model. |
| `AI_EMBEDDING_DIMENSIONS` | `256` | Vector size of the local provider. |
| `APP_STORAGE_LOCATION` | `./data/uploads` | Upload root. Resolved, created and asserted to be a directory. |
| `APP_MAX_FILE_SIZE_BYTES` | `8388608` | Upload ceiling. |
| `APP_CORS_ORIGINS` | localhost dev origins | Explicit allow-list; no wildcard. |
| `RESEARCH_WEB_ENABLED` | `true` | Whether the disclosure may claim web grounding. |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local defaults | Production datasource (`prod` profile). |
| `SERVER_PORT` | `8080` | HTTP port. |
| `APP_SEED_DEMO` | `false` | Opt-in demo dataset (see below). |

**Demo dataset (optional).** Setting `APP_SEED_DEMO=true` creates one demo account with a goal,
tasks and declared skills so the dashboard is not empty on a fresh machine. It is off everywhere
by default, it never touches an existing account, and it is the only code path that creates rows
the user did not.

---

## 11. Running locally

Two terminals.

**Backend** (http://localhost:8080):

```bash
cd backend
mvn spring-boot:run
```

**Frontend** (http://localhost:5173):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173, register an account, and the dashboard is live. The Vite dev server
proxies `/api` to `localhost:8080`, so there is no CORS configuration to fight with.

Handy checks:

```bash
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"Passw0rd123","displayName":"Me"}'
```

Try the command bar with, for example:

```text
Create a task to study Spring Boot tomorrow
Create a 14-day Java interview preparation plan
Create a goal called "Get Java Backend Job"
Show me tasks that are overdue
Show my stats
```

The in-memory H2 console is available at `http://localhost:8080/h2-console` in the `dev` profile
only (JDBC URL `jdbc:h2:mem:cmdcentre`, user `sa`, empty password).

---

## 12. Testing

```bash
cd backend && mvn test     # unit tests and HTTP integration tests
cd frontend && npm run test
```

The suites are independent and offline. Backend: 118 tests (56 unit, 62 HTTP integration).
Frontend: 13 tests.

`mvn test` runs both `*Test` (unit) and `*IT` (full-stack MockMvc against H2) suites, so one
command proves the whole backend.

What is actually covered, beyond the happy paths:

* **Ownership.** Every resource type is exercised with a second account: reads, updates, deletes
  and cross-user attachment all return 404, and the owner's data is verified intact afterwards.
* **The command pipeline.** Unknown intents, undeclared arguments, malformed dates and a tool
  that refuses are all asserted to be rejected *with nothing written*.
* **Upload validation.** Renamed binaries, extension/content mismatches, oversized and empty
  files, and a `../../etc/passwd.md` filename.
* **Honest AI.** An empty knowledge base must answer "not found" rather than guess; a job
  analysis against an empty profile must score 0.
* **Token handling.** Missing, malformed, and forged-signature tokens are all rejected.
* **Determinism.** Chunking, date resolution, keyword ranking and embeddings are tested for
  stable, order-preserving output.

Tests are never deleted or weakened to make a build green; a failing test is a defect to fix.

---

## 13. Docker instructions

```bash
docker compose up --build
```

* `db` — PostgreSQL 16 with a named volume and a health check.
* `backend` — multi-stage Maven build producing a JRE image, running the `prod` profile, waiting
  for the database to be healthy, uploads on a volume.
* `frontend` — multi-stage Node build served by nginx, proxying `/api` to the backend.

Then open <http://localhost:5173>. Set `JWT_SECRET` and `DB_PASSWORD` in a `.env` file next to
`docker-compose.yml` before exposing this to anything other than your own machine — the compose
file deliberately has no production-grade defaults.

Backend-only image:

```bash
cd backend
docker build -t ai-command-center-backend .
docker run -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/commandcenter \
  ai-command-center-backend
```

---

## 14. Security considerations

The full audit — every endpoint, authorisation path, input, upload, AI tool and database
operation — is in **[docs/SECURITY.md](docs/SECURITY.md)**. The properties that carry the design:

* **Passwords.** BCrypt with a cost of 10. Plaintext is never stored, never logged, and there is
  no password field on any response DTO.
* **Tokens.** HS256 with a secret from configuration; the application refuses to start if the
  secret is shorter than 32 bytes. Issuer and expiry are both verified. A valid signature with an
  unknown subject resolves to no principal rather than an error.
* **Object-level authorisation.** Every lookup is `findById(...).filter(row -> row.userId ==
  callerId)`. A missing row and someone else's row are indistinguishable from outside — both 404.
* **SQL injection.** All access is through Spring Data JPA: derived queries, criteria
  specifications and bound JPQL parameters. No string-concatenated SQL anywhere.
* **Uploads.** Extension allow-list, declared content-type allow-list, magic-byte verification,
  size cap, filename reduced to its final path segment, and a storage path built from the user id
  plus a UUID. Every resolved path is asserted to remain inside the storage root.
* **Prompt injection.** Document text can be retrieved into a prompt, but the model's output can
  only ever choose a whitelisted intent with validated primitive arguments. There is no path from
  prompt content to SQL, to an entity, or to a repository call.
* **Error leakage.** Stack traces are excluded at the servlet level and the global handler returns
  one envelope. Provider failures are reduced to a code and a short message — never a request
  body, never a credential.
* **CORS.** An explicit origin allow-list from configuration; credentials are off, because auth is
  a bearer header.
* **Secrets.** `.env` is git-ignored, `.env.example` is documented, and no secret has a
  production-usable default.

---

## 15. Future improvements

Honest list, roughly in the order I would tackle it:

1. **Background ingestion.** Uploads are processed inside the request; at the enforced 8 MB ceiling
   that is sub-second, but a queue would be the right answer if the limit grew.
2. **`pgvector`.** The `VectorStore` interface exists for exactly this. Past roughly a hundred
   thousand chunks, a linear scan stops being the right trade-off.
3. **Streaming responses.** `AiService` returns complete strings; token streaming would improve
   perceived latency for chat and insights.
4. **Refresh tokens.** Access tokens are long-lived (120 minutes) and there is no rotation.
5. **OCR.** Scanned PDFs are correctly refused with an explanatory message rather than failing
   silently; adding OCR would make them usable.
6. **Real web research.** The research agent is deliberately honest about doing no live
   retrieval. Adding a search provider behind the same interface is the obvious next step.
7. **Recursive goal hierarchies** and recurring tasks.
8. **Export.** A GDPR-style export of every row this application holds about a user.

---

## 16. Project structure

```text
cmd-centre/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/aicommandcenter/
│       │   ├── ai/           provider abstraction, command parsing, tool registry, activity log
│       │   ├── analytics/    aggregate read models
│       │   ├── common/       ApiError, PageResponse, JsonLists, enums
│       │   ├── config/       typed @ConfigurationProperties records
│       │   ├── dashboard/    command-centre aggregate
│       │   ├── document/     upload, storage, extraction, chunking, insights
│       │   ├── exception/    exception hierarchy + global handler
│       │   ├── goal/         goals
│       │   ├── health/       liveness endpoint
│       │   ├── job/          job intelligence and skill matching
│       │   ├── notification/ derived notifications
│       │   ├── rag/          embedding, retrieval, answer assembly
│       │   ├── research/     research reports
│       │   ├── security/     JWT, filters, principal, security config
│       │   ├── task/         tasks
│       │   └── user/         users, profile, skills
│       ├── main/resources/   application*.yml, db/migration
│       └── test/java/        unit tests and MockMvc integration tests
├── frontend/
│   ├── src/
│   │   ├── api/          typed client + one function per endpoint + DTO types
│   │   ├── auth/         session provider and the protected-route gate
│   │   ├── components/   shell, command bar, toasts, modal, badges, states
│   │   ├── hooks/        useAsync (one request, one state) and the data-refresh event
│   │   ├── pages/        one file per route
│   │   └── test/         API client, auth gate, command bar and page tests
│   ├── Dockerfile        multi-stage build served by nginx
│   └── nginx.conf        static assets + /api proxy + security headers
├── docs/
│   ├── ARCHITECTURE.md       decisions, data model, frozen constraints
│   ├── API.md                endpoint reference
│   ├── MILESTONES.md         build plan and per-milestone definition of done
│   └── SECURITY.md           security review and threat notes
├── docker-compose.yml
├── .env.example
└── README.md
```

---

Built as a portfolio-grade project: every module has tests, every architectural decision has a
written reason, and nothing in the UI shows a number that is not in the database.
