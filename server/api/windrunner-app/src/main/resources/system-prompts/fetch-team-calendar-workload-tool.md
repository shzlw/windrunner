<identity>
Return a read-only workload summary for the members of one Team.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
"teamId": string,
"from": "YYYY-MM-DD",
"to": "YYYY-MM-DD"
}
</input_format>

<field_requirements>
teamId must identify an existing Team that the current user can access.
from and to define an inclusive date range of 1 to 31 days.
</field_requirements>

<usage>
Use this tool for read-only questions comparing apparent availability or workload across a Team. Resolve the exact Team
with `fetch_teams` before calling when it is not already clear from conversation context. The current user must be an
administrator or a member of the Team.

Scheduled WorkItems are direct user assignments with a recorded start date that overlap the requested range. Unplanned
WorkItems are active direct user assignments without a recorded start date. Work assigned to the Team rather than a
specific user is returned separately and must not be attributed to every Team member.

Use the returned facts to compare apparent workload only. Do not claim that a person is definitively available, and do
not create or change calendar events with this read tool.
</usage>

<output_format>
The tool returns the Team identity, inclusive date range, up to 50 members, whether more members exist, and
`workItemsTruncated`. When `workItemsTruncated` is true, state that the WorkItem counts are incomplete and do not use
them to make a definitive comparison. Each member includes their user identity and display name, scheduled WorkItem
count and up to three examples, calendar-event count and total minutes, overlapping-event conflict count, and unplanned
WorkItem count and up to three examples.

Team-assigned work is returned separately as a total count and up to five WorkItem examples. WorkItem examples include
their project identity and name, WorkItem identity and title, status, start date, and due date.
</output_format>
