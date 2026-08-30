<identity>
Return a complete server-side summary of one project.
</identity>

<usage>
Use this first for project-level status, health, progress, workload, or summary questions. It returns complete totals and distributions for all WorkItems, Entries, and Relationships without sending every record to the model. It also includes the complete count of valid BLOCKED_BY relationships and blocked WorkItems.

Do not use fetch_work_items, fetch_entries, or fetch_relationships with an empty scope to summarize an entire project. Use those tools only afterward for targeted WorkItems or Entries when the summary shows that more detail is needed. For the actual blocker list, call fetch_project_blockers and follow its pagination metadata when completeness is required.
</usage>
