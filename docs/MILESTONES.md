# Milestone plan

Order is fixed. Each milestone ends with build + tests + a review of `git diff` + one commit.

| # | Milestone | Deliverable | Commit message |
|---|---|---|---|
| M0 | Architecture | This document set: architecture, data model, API plan, milestone plan | `docs: define project architecture` |
| M1 | Project foundation | Spring Boot + Vite skeletons, config properties, Flyway `V1`, health endpoint, `ApiError` + global handler | `feat: establish project foundation` |
| M2 | Authentication | Register/login/me, JWT issue+verify, BCrypt, ownership scoping, security tests | `feat(auth): implement secure authentication` |
| M3 | Goals + Tasks | Full CRUD, filtering/sorting/search, goal progress, IDOR tests | `feat(productivity): implement goals and tasks` |
| M4 | Dashboard | Aggregate read model + dashboard UI on real data | `feat(ui): implement command center dashboard` |
| M5 | AI command system | Provider abstraction, parser, whitelisted tools, activity history | `feat(ai): implement command center agent` |
| M6 | Documents | Upload validation, storage, PDF/TXT/MD extraction, pipeline, viewer | `feat(documents): implement document intelligence` |
| M7 | RAG | Chunking, embeddings, retrieval, context assembly, cited answers | `feat(rag): implement knowledge retrieval` |
| M8 | Job intelligence | JD parsing, skill extraction, known/missing/unverified gap analysis, interview prep | `feat(jobs): implement job intelligence` |
| M9 | Research agent | Structured research reports with explicit grounding disclosure | `feat(research): implement research agent` |
| M10 | Notifications + analytics | Derived notifications, analytics from real rows, activity page | `feat(analytics): implement activity and analytics` |
| M11 | Security + QA | Endpoint-by-endpoint audit, fixes, full suite green | `fix: complete security and quality audit` |
| M12 | Production polish | UX states, Docker, README, screenshots, `.env.example` | `chore: finalize production polish` |

## Definition of done (per milestone)

```
compile → unit tests → integration tests → frontend typecheck+build → smoke test → git diff review
```

A failing test is a defect to fix, never a test to delete, disable or weaken.
