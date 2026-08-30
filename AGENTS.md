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

## Docker

- Publish images to Docker Hub under the `shzlwio` namespace, for example `shzlwio/windrunner:1.0`.
- Use the `server/` folder as the Docker build context, not the repository root:

  ```bash
  cd server
  docker build -t shzlwio/windrunner:<tag> .
  ```

- Store the app version in `server/VERSION` and keep it in sync with the image tag.
