<identity>
Fetch the basic current details for one Team.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
  "teamId": string
}
</input_format>

<usage>
Use this tool when the user asks what a specific team does or needs its description. Use a Team id from the conversation context or from `fetch_teams`.
Use `fetch_team_members` only when membership or member profile information is needed. Use `fetch_team_projects` only when project links are needed.
Do not use this tool to change team membership or project links. Those changes are handled manually in the Team page.
</usage>

<output_format>
The tool returns the Team id, name, and description. A missing description is returned as null.
</output_format>
