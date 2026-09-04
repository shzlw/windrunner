# Implementation Principles

- Keep implementations simple and efficient. Prefer the simplest solution that fully satisfies the current requirements.
- Avoid over-engineering. Do not introduce unnecessary abstractions, layers, patterns, frameworks, configuration, or infrastructure for hypothetical future needs.
- Build for current requirements. Do not add extensibility or generalization unless there is a concrete need for it.
- Prefer existing patterns. Reuse existing project conventions, utilities, and dependencies rather than introducing new ones without a clear benefit.
- Minimize change scope. Make the smallest reasonable change necessary to accomplish the task.
- Optimize for readability and maintainability. Prefer straightforward code over clever or overly abstract code.
- When multiple approaches are valid, default to the one with fewer moving parts and lower complexity.

# Repository Rules

## Spring Boot

- Use Spring Boot Starter Data JDBC for all SQL-related functions.
- For inserts, do not use `repository.save`; write explicit `INSERT` SQL statements.
- When an environment variable is the standard Spring Boot relaxed-binding name for a property, set the default directly in `application.properties` instead of duplicating it as `${ENV_VAR:default}`; environment variables override file defaults automatically. Use placeholders only for intentional custom aliases or fallback chains.

## Postgres

- Do not add database foreign keys or `REFERENCES` clauses in schema SQL.
- Keep relationships as plain ID columns and enforce integrity in application logic.

## UI / UX

- Use a trash-can icon only when permanently deleting the underlying record.
- Use an X icon when removing a relationship, pending selection, filter condition, assignee, or another non-destructive row-level association.
- Require explicit confirmation in a popover before sending any request that permanently deletes an underlying record.

## LLM tool calls

- Use progressive fetching for LLM context. The initial prompt should contain only the selected artifact's minimal identity and fields needed for the immediate request.
- Do not preload all projects, work items, entries, users, teams, memberships, or relationships into a prompt.
- Provide focused read tools for additional context and let the model call them only when that context is needed.
- Scope every tool call to the authenticated user and permitted project or entity, and validate access again when loading entities.
- Return compact, bounded tool results with explicit limits or truncation. Do not serialize full domain objects or unbounded collections.
- Prefer a focused batch lookup for known IDs over repeated broad searches when more details are required.
- Keep tool instructions accurate about each tool's returned fields and intended use.
- Keep write operations separate from read tools. Use an explicit proposal or confirmation flow for mutations, and never claim a mutation was applied unless it was actually persisted.
- Before an LLM write proposes an ADD, require a targeted read/search for an existing match. Report a clear match and use UPDATE with its exact ID when the user wants to change it; do not guess between ambiguous matches; only propose ADD after no clear match is found.

## Documentation

- Document only behavior and features that exist in the product. If a claim is uncertain, check the actual code, routes, UI, or configuration before writing it.
- Do not document planned, implied, or hypothetical features as if they are available.
- Keep implementation details out of user-facing documentation unless they are necessary for a user to complete a task.
- Focus on information users need and the value it provides. Use clear, direct, friendly language and explain what users can do and what to expect.
- Keep conceptual documentation separate from task-oriented guides and technical reference material.
- When updating documentation, check links, navigation, formatting, and the documentation build before finishing.

## Docker

- Publish images to Docker Hub under the `shzlwio` namespace, for example `shzlwio/windrunner:1.0`.
- Use the `server/` folder as the Docker build context, not the repository root:

  ```bash
  cd server
  docker build -t shzlwio/windrunner:<tag> .
  ```

- Store the app version in `server/VERSION` and keep it in sync with the image tag.
