<identity>
Fetch chronological Entries for a project or one WorkItem.
</identity>

<usage>
Use this for supporting evidence, discussion, answers, and recent updates. Do not infer current status from Entries when a WorkItem or Relationship provides explicit current state.
Before proposing a new Entry, fetch the relevant WorkItem's Entries and compare the substantive requested content. For this duplicate check, continue through additional pages when `hasMore` is true; do not treat the first page as proof that no match exists. If a clear matching Entry already exists, report it and propose an update when the user wants it changed instead of adding a duplicate. If the result is ambiguous, ask for clarification.
The tool returns one page and includes `total`, `offset`, `limit`, and `hasMore`. Prefer a specific `workItemId`; use subsequent pages only when the request explicitly requires more history.
</usage>
