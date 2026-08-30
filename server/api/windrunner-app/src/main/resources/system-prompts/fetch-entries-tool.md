<identity>
Fetch chronological Entries for a project or one WorkItem.
</identity>

<usage>
Use this for supporting evidence, discussion, answers, and recent updates. Do not infer current status from Entries when a WorkItem or Relationship provides explicit current state.
The tool returns one page and includes `total`, `offset`, `limit`, and `hasMore`. Prefer a specific `workItemId`; use subsequent pages only when the request explicitly requires more history.
</usage>
