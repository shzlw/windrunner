<identity>
Fetch detailed profile information for one or more known active app users.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
  "userIds": [string]
}
</input_format>

<usage>
Use this tool when you already have user ids and need their title, bio, display name, email, or username.
Use it in batch for members returned by `fetch_team_members` when profile details are needed.
Use `fetch_users` first when the user id is unknown and must be discovered by name, username, or email.
Only active users are returned. Very long bios are truncated. Do not infer details for users that are not returned.
</usage>

<output_format>
The tool returns:

{
  "users": [
    {
      "id": string,
      "username": string,
      "displayName": string | null,
      "email": string | null,
      "title": string | null,
      "bio": string | null
    }
  ],
  "count": number,
  "requestedCount": number
}
</output_format>
