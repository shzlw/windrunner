<identity>
Return WorkItem-to-WorkItem BLOCKED_BY relationships in one project.
</identity>

<usage>
Use this for project-level blocker questions. The result is aggregated server-side into compact blocker records and returned one page at a time. Use `total`, `offset`, `limit`, and `hasMore` to determine whether more pages are needed. Use the returned WorkItem IDs with targeted read tools only when entry evidence or additional fields are needed. Do not infer a blocker when no relationship is returned.
</usage>

<input_format>
{
  "projectId": string,
  "limit": number | null,
  "offset": number | null
}
</input_format>
