package com.windrunner.server.work.api;

/**
 * The optional before item is a sibling WorkItem or Entry in the destination content stream.
 */
public record WorkItemMoveRequest(String parentWorkItemId, String beforeEntityType, String beforeEntityId) {
}
