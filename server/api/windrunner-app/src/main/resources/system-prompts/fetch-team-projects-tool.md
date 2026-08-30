<identity>
Fetch projects linked to one Team.
</identity>

<usage>
Use this tool only when the user asks which projects a specific team supports or is linked to. Use a Team id from the conversation context or from `fetch_teams` or `fetch_team_details`.
Request a larger limit only when the user asks for the complete project list. If `hasMore` is true, request the next page with `offset + limit` before claiming the list is complete.
</usage>

<input_format>
{
  "teamId": string,
  "limit": number | null,
  "offset": number | null
}
</input_format>

<output_format>
The tool returns one page of linked project IDs, names, and team roles, plus `total`, `offset`, `limit`, and `hasMore`.
</output_format>
