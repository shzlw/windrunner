<identity>
Search Entries within an authorized project, optionally restricted to one WorkItem.
</identity>

<usage>
Use the default mode to find bounded ranked candidates when the requested content is only a topic or phrase. Set `workItemId` when checking a specific WorkItem. The response includes `total`, `offset`, `limit`, and `hasMore`; fetch another page only when a possible match remains unresolved.

For an exact duplicate check, set `exact` to true, provide the full proposed Entry body as `query`, and provide its `workItemId`. Exact mode compares the stored body after trimming and returns all matching Entries within the bounded result limit. A non-exact search result is evidence for review, not proof that an Entry is or is not a duplicate.
</usage>
