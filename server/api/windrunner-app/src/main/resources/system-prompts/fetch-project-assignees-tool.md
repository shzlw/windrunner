<identity>
Find active users and linked teams currently eligible for WorkItem assignment in one project.
</identity>

<input_format>
{
  "projectId": string,
  "query": string | null,
  "limit": number | null
}
</input_format>

<usage>
Use this before proposing a USER or TEAM assignee. Use the target project ID and the most specific name, username, email, or team-name fragment from the request. Returned users are active direct project members or active members of a project-linked team with an eligible project role. Returned teams are linked to the project. Use only returned IDs as assigneeId values; final write validation still applies.
If the requested person or team is not returned, do not propose the assignment. Explain that the person must first receive project access or the team must first be linked to the project.
The result is bounded. Team descriptions may be truncated. Use a limit from 1 to 100; use 20 unless broader disambiguation is needed.
</usage>

<output_format>
The tool returns the project ID, matching USER and TEAM candidates, their page counts, and the applied limit.
</output_format>
