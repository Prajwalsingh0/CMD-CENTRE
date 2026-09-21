# API reference

Base URL: `http://localhost:8080`. All bodies are JSON unless stated.
Every `/api/**` route except `POST /api/auth/register`, `POST /api/auth/login` and
`GET /api/health` requires `Authorization: Bearer <accessToken>`.

Conventions

* `POST` create → `201 Created` · `DELETE` → `204 No Content` · everything else → `200 OK`.
* Lists that can grow are paginated with `PageResponse<T>`; short, bounded lists are arrays.
* Every error uses the same envelope:

```json
{
  "timestamp": "2026-01-31T09:12:44.221Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Task not found: 91",
  "path": "/api/tasks/91",
  "details": [{ "field": "title", "issue": "must not be blank" }]
}
```

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Liveness + build/version info. Public. |
| POST | `/api/auth/register` | `{email, password, displayName}` → `AuthResponse`. Public. |
| POST | `/api/auth/login` | `{email, password}` → `AuthResponse`. Public. |
| GET | `/api/auth/me` | Current `UserResponse`. |
| GET | `/api/users/me` | Profile incl. skills. |
| PUT | `/api/users/me` | `{displayName?, headline?}` → `UserResponse`. |
| GET | `/api/users/me/skills` | `SkillResponse[]`. |
| POST | `/api/users/me/skills` | `{skill, level?, verified?}` → `201 SkillResponse`. |
| DELETE | `/api/users/me/skills/{skillId}` | Remove a skill. |
| GET | `/api/goals` | `?status=ACTIVE\|PAUSED\|COMPLETED\|ARCHIVED` → `GoalResponse[]`. |
| POST | `/api/goals` | `{title, description?, deadline?, priority?, status?, notes?}`. |
| GET | `/api/goals/{id}` | `GoalResponse` (progress derived from tasks). |
| PUT | `/api/goals/{id}` | Partial merge of the same fields. |
| POST | `/api/goals/{id}/refresh-status` | Auto-complete a goal whose tasks are all done. |
| DELETE | `/api/goals/{id}` | Deletes the goal, **detaches** its tasks. |
| GET | `/api/tasks` | `?status=&statuses=&priority=&goalId=&search=&dueBefore=&overdueOnly=&page=&size=&sort=` → `PageResponse<TaskResponse>`. |
| GET | `/api/tasks/overdue` | `TaskResponse[]`. |
| POST | `/api/tasks` | `{title, description?, status?, priority?, dueDate?, goalId?, tags?}`. |
| GET | `/api/tasks/{id}` | `TaskResponse`. |
| PUT | `/api/tasks/{id}` | Partial merge. |
| PATCH | `/api/tasks/{id}/status` | `{status}` → `TaskResponse`. |
| DELETE | `/api/tasks/{id}` | Delete a task. |
| GET | `/api/documents` | `?status=&search=` → `DocumentResponse[]`. |
| POST | `/api/documents` | `multipart/form-data` field `file` (pdf/txt/md, ≤8 MB) → `201`. |
| GET | `/api/documents/{id}` | Metadata. |
| GET | `/api/documents/{id}/content` | `{id, name, text, truncated}` extracted text preview. |
| POST | `/api/documents/{id}/insights` | `{operation: SUMMARY\|KEY_POINTS\|NOTES\|QUESTIONS}` → `DocumentInsightResponse`. |
| DELETE | `/api/documents/{id}` | Removes file + chunks. |
| POST | `/api/knowledge/ask` | `{question, documentIds?, topK?}` → `AnswerResponse` with cited sources. |
| POST | `/api/knowledge/reindex` | Re-chunk + re-embed the user's documents → `ReindexResponse`. |
| GET | `/api/jobs` | `JobAnalysisResponse[]`. |
| POST | `/api/jobs/analyze` | `{description, jobTitle?, company?}` → full analysis. |
| GET | `/api/jobs/{id}` | One analysis. |
| POST | `/api/jobs/{id}/prep` | Regenerate interview prep for a stored analysis. |
| DELETE | `/api/jobs/{id}` | Delete an analysis. |
| GET | `/api/research` | `ResearchResponse[]`. |
| POST | `/api/research` | `{topic, depth?}` → `ResearchResponse`. |
| GET | `/api/research/{id}` | One report. |
| DELETE | `/api/research/{id}` | Delete a report. |
| POST | `/api/ai/command` | `{command}` → `CommandResultResponse` (intent, status, steps, payload). |
| POST | `/api/ai/chat` | `{message, history?}` → `ChatResponse`. |
| GET | `/api/ai/activity` | `?limit=` → `AiActivityResponse[]`. |
| GET | `/api/ai/status` | Active provider + capability flags. |
| DELETE | `/api/ai/activity` | Clear history. |
| GET | `/api/dashboard` | Single aggregate call for the command centre. |
| GET | `/api/analytics` | `?days=30` → counters, completion rate, series. |
| GET | `/api/notifications` | `?unreadOnly=` → `NotificationResponse[]`. |
| GET | `/api/notifications/unread-count` | `{count}`. |
| POST | `/api/notifications/refresh` | Derive deadline/overdue/completion notifications. |
| POST | `/api/notifications/{id}/read` | Mark one read. |
| POST | `/api/notifications/read-all` | Mark all read. |
| DELETE | `/api/notifications/{id}` | Delete one. |

## AI command intents (whitelist)

`CREATE_TASK`, `CREATE_GOAL`, `CREATE_GOAL_WITH_PLAN`, `LIST_TASKS`, `LIST_OVERDUE_TASKS`,
`COMPLETE_TASK`, `UPDATE_TASK_STATUS`, `SUMMARIZE_DOCUMENT`, `ASK_KNOWLEDGE_BASE`,
`ANALYZE_JOB`, `RESEARCH_TOPIC`, `CREATE_RESEARCH_REPORT`, `SHOW_STATS`, `UNKNOWN`.

Anything else is rejected with `status = "REJECTED"` and a reason. The model never receives a
database handle — only this intent string and primitive arguments, which are re-validated by
the tool that runs them.
