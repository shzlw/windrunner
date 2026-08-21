# Repository Rules

## Spring Boot

- Use Spring Boot Starter Data JDBC for all SQL-related functions. For inserts, do not use `repository.save`; write explicit `INSERT` SQL statements.

## Postgres

- Do not add database foreign keys or `REFERENCES` clauses in schema SQL. Keep relationships as plain ID columns and enforce integrity in application logic.

## UI / UX

- UI icon convention: use a trash-can icon only when permanently deleting the underlying record. Use an X icon when removing a relationship, a pending selection, a filter condition, an assignee, or another non-destructive row-level association.
- UI deletion convention: every action that permanently deletes an underlying record must require explicit confirmation in a popover before the delete request is sent.
