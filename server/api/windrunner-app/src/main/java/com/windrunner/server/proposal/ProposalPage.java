package com.windrunner.server.proposal;

import java.util.List;

public record ProposalPage(List<ProposalView> items, boolean hasMore, int offset, int limit) {
}
