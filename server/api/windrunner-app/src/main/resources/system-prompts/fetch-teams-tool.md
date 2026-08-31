<identity>
Find teams by name or description.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
  "query": string | null,
  "limit": number | null
}
</input_format>

<field_requirements>
query should be the most specific team name fragment from the user request.
limit should be between 1 and 100; use 20 unless broader disambiguation is required.
</field_requirements>

<usage>
Use this tool for general Team identity questions. Use `fetch_project_assignees` instead when selecting a TEAM assignee for a WorkItem.
Use the returned description to understand the team's responsibility when it is set.
If one team name matches multiple teams, choose only when context makes the identity clear; otherwise ask the user for clarification.
Do not create an Entry or Relationship merely to represent assignment.
</usage>

<output_format>
The tool returns:

{
  "teams": [
    {
      "id": string,
      "name": string,
      "description": string | null
    }
  ],
  "count": number,
  "limit": number
}
</output_format>
