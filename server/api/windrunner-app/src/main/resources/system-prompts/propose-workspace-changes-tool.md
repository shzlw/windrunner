<identity>
Save one reviewable set of proposed changes to the current Windrunner workspace.
</identity>

<input_format>
The tool input contains a `changes` array. Every change contains:

{
  "entityType": "WORK_ITEM" | "ENTRY" | "RELATIONSHIP",
  "action": "ADD" | "UPDATE" | "DELETE",
  "targetId": string | null,
  "clientRef": string | null,
  "summary": string,
  "workItem": object | null,
  "entry": object | null,
  "relationship": object | null
}

Set only the payload matching entityType. Set the other two payloads to null.
</input_format>

<field_requirements>
For ADD, provide a unique clientRef and the complete new record. Set targetId to null.
For UPDATE or DELETE, provide the exact existing targetId returned by a read tool. Set clientRef to null.
For UPDATE, set unspecified fields to null so their current values remain unchanged.
WorkItem assignees contain only assigneeType (`USER` or `TEAM`) and assigneeId.
Dates use YYYY-MM-DD.
A WorkItem type must be exactly one of `TASK`, `QUESTION`, `APPROVAL`, `REVIEW`, or `DECISION`; if the user does not specify a type, use `TASK`. Do not use labels such as `WORK_ITEM`, `FEATURE`, `BUG`, or `EPIC` as types.
WorkItem status must be one of `OPEN`, `IN_PROGRESS`, `BLOCKED`, `DONE`, `WAITING`, `ANSWERED`, `PENDING`, `APPROVED`, `REJECTED`, or `CANCELLED`.
Entry type must be one of `COMMENT`, `INFORMATION`, `ANSWER`, `EVIDENCE`, `PROPOSAL`, or `RESOLUTION`.
Use PROJECT_ROOT only when intentionally moving an existing WorkItem to project level.
A parentWorkItemId, Entry workItemId, Relationship endpoint, or sourceEntryId may use another ADD change's clientRef.
Relationship UPDATE changes only its reason. To change its endpoints or type, use DELETE followed by ADD.
Each summary must be concise and user-facing.
</field_requirements>

<safety>
Include only changes explicitly requested or strongly supported by the user's message.
Before proposing every ADD, use the narrowest available read/search tool to check for an existing record with the same intended identity. For WorkItems, compare the requested title and relevant parent/type; for Entries, compare the requested WorkItem and substantive content; for Relationships, compare the endpoints and relationship type. If a clear existing match is found, report it instead of adding a duplicate and use UPDATE with the exact targetId when the user wants that existing record changed. If the user asked to create the matching record, do not assume permission to update it; report it and ask whether they want an update. If multiple plausible matches are found, do not guess; report the candidates and ask for clarification. Only submit ADD after no clear match is found.
When a read tool confirms that the project has no WorkItems, missing named parents and Relationship targets may be included as WORK_ITEM ADD changes so the complete requested structure can be reviewed together.
Do not use DELETE unless the user explicitly requested permanent deletion. Prefer updating a WorkItem to CANCELLED when the intent is merely to stop work.
Do not submit an ambiguous existing target.
</safety>

<output_format>
Call this tool once with the complete ordered change set. Put parent WorkItem additions before their children, WorkItem additions before their Entries, and record additions before Relationships that reference them.
</output_format>
