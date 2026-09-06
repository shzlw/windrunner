package com.windrunner.server.work.api;

import java.util.List;

public record ProposalDraft(List<ChangeDraft> changes) {
}
