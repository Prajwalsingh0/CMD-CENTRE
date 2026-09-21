# AI Command Center — Architecture (M0)

Status: **approved baseline**. Everything below is the contract for the rest of the build.
Changes require an explicit, documented justification (see "Architecture change policy").

## 1. Product summary

A single-user-centric personal AI workspace. One authenticated user owns goals, tasks,
documents, a document knowledge base (RAG), job analyses, research reports, AI activity
history and notifications. A global **AI Command Bar** turns natural language into
*validated, whitelisted* application actions.

## 2. Technology decisions

| Concern | Decision | Why |
|---|---|---|
| Language | Java 21 | Target stack in the brief; LTS. Compiled with `--release 21` so the JDK on the build machine (23) stays irrelevant. |
| Backend framework | Spring Boot 3.4.x, Maven | Brief. Maven over Gradle: single obvious convention, no wrapper bootstrapping needed on Windows. |
| Persistence | Spring Data JPA + Flyway | Brief. Flyway owns the schema; `ddl-auto: validate` guarantees entity/migration drift fails loudly at startup. |
| Production DB | PostgreSQL | Brief. |
| Dev/test DB | H2 in-memory, `MODE=PostgreSQL`, `DATABASE_TO_LOWER=TRUE` | Brief asks for H2 in tests; the PostgreSQL compatibility mode lets the *same* Flyway migration run on both, so migrations are proven before prod. |
| Auth | Stateless JWT (HS256, `jjwt`), BCrypt(10) password hashing | Brief. Stateless keeps the API horizontally scalable and removes session/CSRF surface. |
| Frontend | React 18 + TypeScript + Vite | Brief. No state-management or UI framework added: a ~120-line typed API client + React context is enough for this surface area and keeps the dependency graph auditable. |
| Charts | Hand-rolled SVG | Avoids a charting dependency and, more importantly, avoids fabricated data — every series is computed from real rows. |
| AI | `AiService` interface with two implementations: `local` (deterministic, no credentials, default) and `openai` (HTTPS) | Brief demands provider isolation. A credential-free provider is what makes the project reproducible for a reviewer and testable in CI. |
| Vector search | Embeddings stored as JSON text in `document_chunks`, cosine similarity computed in the service layer | See "RAG storage decision". |
| Infra | Docker + docker-compose. No Redis, no WebSockets, no message broker. | Nothing in the feature set needs them — the brief explicitly forbids decoration-driven infrastructure. |

### Deliberately rejected

* **pgvector / a dedicated vector DB** — rejected *for now*, not forever. The corpus is
  user-uploaded personal documents (tens of MB). At that scale a brute-force cosine scan
  over a few thousand 256-dimension vectors is sub-millisecond and needs zero extra
  infrastructure. The repository interface is written so a `PgVectorStore` can replace
  `JpaVectorStore` without touching callers. Rationale is recorded in
  [docs/SECURITY.md](SECURITY.md) and in `VectorStore`'s Javadoc.
* **Spring AI / LangChain4j** — an extra abstraction layer over an abstraction layer. The
  provider interface here is ~40 lines and fully under our control.
* **Redis caching** — no measured hot path to cache; the DB is local to the app.

## 3. Layered architecture

```
        HTTP
         │
   ┌─────▼─────┐   request DTO + Bean Validation
   │Controller │   no business logic, no entities
   └─────┬─────┘
         │
   ┌─────▼─────┐   business rules, ownership checks, transactions
   │  Service  │◄──────────────┐
   └─────┬─────┘               │
         │                     │
   ┌─────▼─────┐         ┌─────┴──────┐
   │Repository │         │ AI provider│ (AiService)
   └─────┬─────┘         └────────────┘
         │
   ┌─────▼─────┐
   │  Entity   │  ← never leaves the service layer
   └───────────┘
```

Hard rules enforced throughout the codebase:

1. Controllers return DTOs (`*Response` records), never entities.
2. Every repository call is scoped by `userId`; "not yours" is reported as **404**, never 403,
   so the API cannot be used to probe for the existence of another user's rows.
3. Transactions are declared on service methods, not controllers.
4. Cross-module access goes through the other module's *service*, not its repository.
5. All failures funnel through `GlobalExceptionHandler` into the single `ApiError` shape.

## 4. Package structure

```
com.aicommandcenter
├── ai/           AiService abstraction, providers, command parsing, tool registry, activity log
│   ├── provider/ local + openai implementations
│   ├── command/  intent model, parser, whitelisted tools, orchestration
│   └── activity/ AI activity history entity/service/controller
├── analytics/    aggregate read models (dashboard + analytics endpoints)
├── common/       ApiError, PageResponse, JsonLists, enums
├── config/       typed @ConfigurationProperties records
├── dashboard/    command-centre aggregate endpoint
├── document/     upload, storage, text extraction, chunking
├── exception/    ApiException hierarchy + GlobalExceptionHandler
├── goal/         goals (controller/service/repository/entity/dto)
├── health/       liveness endpoint
├── job/          job-description intelligence + skill gap analysis
├── notification/ derived notifications
├── rag/          embedding + vector store + retrieval + answer assembly
├── research/     research reports
├── security/     JWT, filters, principal, SecurityConfig
├── task/         tasks
└── user/         users, profile, skills
```

Rationale for **package-by-feature** rather than package-by-layer: each module is
independently reviewable, and a future extraction into a service would be a directory move
rather than a refactor.

## 5. Domain model

```
User ─1───n Goal ─1───n Task            (task.goal_id nullable, ON DELETE SET NULL)
  │
  ├─1───n UserSkill                     (the ONLY source of "known skill" truth)
  ├─1───n Document ─1───n DocumentChunk (chunk.embedding = JSON vector text)
  ├─1───n JobAnalysis
  ├─1───n ResearchReport
  ├─1───n AiActivity
  └─1───n Notification                  (unique (user_id, dedupe_key))
```

Notes:

* `Task.goalId` is a plain FK column, not a JPA association. The reason is deliberate: the
  API never needs to serialise a graph, and owning-side/managed-entity cascade rules are the
  most common source of accidental cross-user writes. Ownership is always re-checked by id.
* Deleting a goal **detaches** its tasks instead of cascading — the product must never
  silently destroy user work. This is implemented in `GoalService#delete`.
* Collections that are small, ordered and read as a unit (`tags`, `requiredSkills`, embedding
  vectors) are stored as TEXT/JSON through `JsonLists` so the schema stays identical on H2
  and PostgreSQL without dialect-specific column types.

## 6. AI architecture

### 6.1 Provider abstraction

```java
public interface AiService {
    String providerName();
    boolean isRemote();
    AiCompletion complete(AiRequest request);   // system + messages + optional JSON mode
    List<double[]> embed(List<String> texts);
}
```

* `LocalAiService` — deterministic, credential-free. Extractive summarisation (sentence
  scoring), keyword extraction, JSON-shaped command parsing through the same regex/heuristic
  parser the system uses as a fallback, and a hashed bag-of-words embedding. It is the default
  so `git clone && mvn spring-boot:run` is a working product.
* `OpenAiService` — `RestClient` against `/chat/completions` and `/embeddings`, with a
  hard timeout, response size sanity checks and no credential leakage into logs.

Selection happens once in `AiConfig` from `app.ai.provider`. **No other class knows which
implementation is live**; that is what makes the abstraction real rather than decorative.

### 6.2 Command pipeline (the security-critical path)

```
POST /api/ai/command  { "command": "create a task ..." }
        │
        ▼
AiCommandService          ← records an AiActivity row in every case
        │
        ▼
AiCommandParser           LLM (JSON mode) → validated; on any provider failure or
        │                 malformed output → deterministic heuristic parser
        ▼
ParsedCommand(intent, args, confidence, rationale)
        │
        ▼
CommandToolRegistry       ← WHITELIST: intent → CommandTool. Unknown intent is rejected.
        │
        ▼
CommandTool (one per intent)  ← validates every parameter itself
        │
        ▼
existing services (TaskService, GoalService, DocumentService, JobService, RagService …)
        │
        ▼
Database
```

The model **cannot** express SQL, an entity name or a repository call. Its only output is an
intent string plus a flat map of primitive arguments. Unknown intents, unknown argument
names, unparsable dates and out-of-range values are rejected before any service is invoked.
This is what the brief means by "the AI must never bypass the service layer" — it is enforced
by the type system plus a validation gate, not by prompt wording.

### 6.3 RAG pipeline

```
upload → validate (extension, declared content-type, size, magic bytes, path traversal)
      → store under <root>/<userId>/<uuid>.<ext>
      → extract text (PDFBox | plain reader)
      → chunk (~900 chars, 150 overlap, paragraph-aware)
      → embed (AiService.embed)
      → persist chunks + vectors
      → status = READY
ask   → embed question → cosine top-k over the user's chunks → assemble context with
        document names → AiService.complete → answer + cited sources
```

Retrieval is always filtered by `userId` before ranking, so RAG cannot be used to read
another user's documents.

## 7. API surface

See [docs/API.md](API.md) for the full table. Conventions:

* `/api/**` except `register`, `login`, `health` requires `Authorization: Bearer <jwt>`.
* `POST` create → `201`, `DELETE` → `204`, everything else → `200`.
* Errors → the single `ApiError` shape with `timestamp, status, error, message, path, details[]`.
* Pagination → `PageResponse<T>` (`items, page, size, totalItems, totalPages, hasNext`).

## 8. Configuration & secrets

Typed `@ConfigurationProperties` records under `config/`. Everything environment-derived has
a `${ENV:default}` placeholder, and every default is safe for local development only:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HS256 signing key, ≥32 bytes. Startup fails fast if shorter. |
| `AI_PROVIDER` | `local` (default) or `openai`. |
| `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` | Only read when `AI_PROVIDER=openai`. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Production datasource (`prod` profile). |
| `APP_STORAGE_LOCATION` | Upload root; resolved, created, and asserted to be a directory. |
| `APP_CORS_ORIGINS` | Comma-separated allow-list, no wildcard. |
| `RESEARCH_WEB_ENABLED` | Whether the research agent may claim web grounding. |

`.env.example` documents all of them; `.env` is git-ignored. No secret is ever committed.

## 9. Architecture change policy

Once implementation started, the following are frozen: framework choices, database, language,
folder structure, authentication strategy, layering rules, and the AI provider boundary. A
change is only allowed with: (a) the concrete technical problem, (b) the proposed change,
(c) the blast radius, and (d) explicit approval. Anything else is a bug, not a redesign.
