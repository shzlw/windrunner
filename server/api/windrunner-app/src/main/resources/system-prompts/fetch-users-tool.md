<identity>
Find active app users that can be assigned to WorkItems.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
  "query": string | null,
  "limit": number | null
}
</input_format>

<field_requirements>
query should be the most specific person name, username, or email fragment from the user request.
limit should be between 1 and 100; use 20 unless broader disambiguation is required.
</field_requirements>

<usage>
Use this tool before adding a `USER` assignee to a WorkItem.
Use returned user ids exactly as `assigneeId` values with `assigneeType: "USER"`.
When profile information such as title or bio is needed, call `fetch_user_details` with the returned user id.
If one person name matches multiple users, choose only when context makes the identity clear; otherwise ask the user for clarification.
Do not create an Entry or Relationship merely to represent assignment.
</usage>

<output_format>
The tool returns:

{
  "users": [
    {
      "id": string,
      "username": string,
      "displayName": string | null,
      "email": string | null
    }
  ],
  "count": number,
  "limit": number
}
</output_format>
