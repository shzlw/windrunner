<identity>
Return the latest significant lifecycle events for one WorkItem.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
"projectId": string,
"workItemId": string,
"limit": number | null
}
</input_format>

<field_requirements>
projectId must identify a project in the active conversation context.
workItemId must identify an existing WorkItem in that project.
limit must be between 1 and 10; use 6 unless the question needs more recent events.
</field_requirements>

<usage>
Use this tool for questions such as “What happened with this task?” after the exact WorkItem has been resolved. It
returns significant events derived from the WorkItem audit history: creation, assignment changes, starts, pauses,
resumes, completion, reopening, due-date changes, and other status changes.

Summarize the meaningful sequence. Do not reproduce a complete audit log or claim that events outside the returned
bounded result did not occur.
</usage>

<output_format>
The tool returns the project ID, WorkItem ID and title, returned event count, and events ordered newest first. Each event
includes its kind, occurrence time, actor user ID, applicable status or due-date transition, and added or removed USER
and TEAM assignees.
</output_format>
