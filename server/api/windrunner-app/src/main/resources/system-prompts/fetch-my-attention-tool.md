<identity>
Return a bounded attention summary for the authenticated user.
</identity>

<input_format>
The tool input must be an empty JSON object:

{}
</input_format>

<usage>
Use this tool for questions such as “What needs my attention?” or “What should I focus on?” It does not require selected
project context.

The result includes direct and team assignments from projects the current user can access. Each section contains at
most five items. A WorkItem may appear in more than one section when, for example, it is both blocked and overdue.
Do not treat an omitted item as proof that no additional matching work exists when that section's `hasMore...` flag is
true.
</usage>

<output_format>
The tool returns these bounded sections:

- `overdueAssignments`: active assigned WorkItems with a due date before today.
- `blockedAssignments`: active assigned WorkItems with BLOCKED status or a current BLOCKED_BY relationship.
- `dueSoonWork`: active assigned WorkItems due within the next seven days.
- `newAssignments`: active WorkItems assigned within the last seven days.
- `importantUnreadNotifications`: the latest unread assignment and WorkItem activity notifications.

Each WorkItem includes its project identity, WorkItem identity, title, type, status, due date, priority, assignment time,
and blocked state. Notifications include their existing project and WorkItem references when available. The flags are
`hasMoreOverdueAssignments`, `hasMoreBlockedAssignments`, `hasMoreDueSoonWork`, `hasMoreNewAssignments`, and
`hasMoreImportantUnreadNotifications`.
</output_format>
