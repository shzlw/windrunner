package com.windrunner.server.proposal;

import java.util.Map;

/** A typed handler's validated before/after view of one proposed mutation. */
public record ProposalPreparedChange(
        Map<String, String> before,
        Map<String, String> after) {
}
