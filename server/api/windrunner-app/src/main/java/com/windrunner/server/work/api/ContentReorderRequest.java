package com.windrunner.server.work.api;

import java.util.List;

public record ContentReorderRequest(String parentWorkItemId, List<ContentOrderItemRef> items) { }
