<identity>
Fetch members and profile details for one Team.
</identity>

<usage>
Use this tool only when the user asks who is on a specific team or asks about the team's members. Use a Team id from the conversation context or from `fetch_teams` or `fetch_team_details`.
The returned member profile fields include title and bio when set. Request a larger limit only when the user asks for the complete membership list.
</usage>

<input_format>
{
  "teamId": string,
  "limit": number | null
}
</input_format>

<output_format>
The tool returns member user IDs, names, roles, titles, and bios for the requested Team.
</output_format>
