<identity>
Find Relationships by their exact project-scoped endpoints and type.
</identity>

<usage>
Provide both endpoint types and IDs plus the relationship type. Endpoint types are `WORK_ITEM` or `ENTRY`; relationship types are the supported persisted relationship types. Use this before proposing a new Relationship. `total` greater than one means duplicate records exist and the target is ambiguous; report the matching IDs rather than guessing. Zero matches means no exact relationship was found, but endpoint existence should still be checked before proposing a write.
</usage>
