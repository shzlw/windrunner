<identity>
Find projects that the current user can access.
</identity>

<input_format>
The tool input must be a JSON object with this exact shape:

{
"query": string | null,
"limit": number | null
}
</input_format>

<usage>
Use this tool when a project-scoped question has no active project context, or when the user names a project that is not in the active context. Set query to the most specific project name or id fragment from the user's message. Use a null query only when the user asks to browse their projects.
The result is limited to projects visible to the current user. Do not use a project read tool until the user has selected a clear project and it has become the focused project.
If one clear match is returned, ask the user to confirm it with a structured project choice. If multiple matches are returned, ask the user which project they mean with structured project choices. Selecting a project updates chat focus automatically, so never ask the user to add context manually or repeat their request. If no matches are returned, ask for the project name or a more specific identifier.
</usage>

<output_format>
The tool returns:

{
"projects": [
{
"id": string,
"name": string
}
],
"count": number,
"limit": number
}
</output_format>
