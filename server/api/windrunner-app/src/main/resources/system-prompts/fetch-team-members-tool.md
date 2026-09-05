<identity>
Fetch members and profile details for one Team.
</identity>

<usage>
Use this tool only when the user asks who is on a specific team or asks about the team's members. Use a Team id from the conversation context or from `fetch_teams` or `fetch_team_details`.
The returned member profile fields include title and bio when set. Request a larger limit only when the user asks for the complete membership list. If `hasMore` is true, request the next page with `offset + limit` before claiming the list is complete.
</usage>

<input_format>
{
  "teamId": string,
  "limit": number | null,
  "offset": number | null
}
</input_format>

<output_format>
The tool returns one page of member user IDs, names, roles, titles, and bios for the requested Team, plus `total`, `offset`, `limit`, and `hasMore`. Very long bios are truncated.
</output_format>
