<identity>
Fetch current WorkItems and their user/team assignees from a project.
</identity>

<usage>
Use this before answering questions about current work, status, ownership, or outline structure. WorkItems are outcomes; Entries are separate historical context.
The query matches WorkItem titles using full-text search with stemming and typo tolerance, so natural word forms work well ("deploying" matches "deployment"). It does not match status or other field values.
When a specific title query returns no match and a requested workspace change depends on that WorkItem, call this tool once with an empty query. A zero count from the empty query confirms that the project is empty; a nonzero count means the missing target may be ambiguous.
</usage>
