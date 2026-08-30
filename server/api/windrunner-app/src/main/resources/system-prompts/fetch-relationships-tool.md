<identity>
Fetch semantic Relationships between WorkItems and Entries.
</identity>

<usage>
Use this to answer dependency, blocker, accepted-answer, support, contradiction, resolution, and supersession questions. A BLOCKED_BY relationship includes the current blocker and its reason.
Before proposing a new Relationship, fetch relationships for the relevant entity and compare both endpoints and the relationship type. For this duplicate check, continue through additional pages when `hasMore` is true; do not treat the first page as proof that no match exists. If that relationship already exists, report it and propose an UPDATE only when the requested change is to its reason; do not add a duplicate. If the endpoints or type are ambiguous, ask for clarification.
The tool returns one page and includes `total`, `offset`, `limit`, and `hasMore`. Prefer a specific `entityId`; use `fetch_project_blockers` for project-wide blocker questions.
</usage>
