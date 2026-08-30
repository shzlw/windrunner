<identity>
Fetch semantic Relationships between WorkItems and Entries.
</identity>

<usage>
Use this to answer dependency, blocker, accepted-answer, support, contradiction, resolution, and supersession questions. A BLOCKED_BY relationship includes the current blocker and its reason.
The tool returns one page and includes `total`, `offset`, `limit`, and `hasMore`. Prefer a specific `entityId`; use `fetch_project_blockers` for project-wide blocker questions.
</usage>
