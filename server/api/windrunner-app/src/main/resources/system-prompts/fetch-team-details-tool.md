<identity>
Fetch the current details for one Team.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
  "teamId": string
}
</input_format>

<usage>
Use this tool when the user asks what a specific team does, who is on it, or which projects it supports. Use a Team id from the conversation context or from `fetch_teams`. Each returned member already includes the user's title and bio; use those fields directly instead of making one user lookup per member.
Do not use this tool to change team membership or project links. Those changes are handled manually in the Team page.
</usage>

<output_format>
The tool returns the Team description, members, and linked projects. A missing description is returned as null.
</output_format>
