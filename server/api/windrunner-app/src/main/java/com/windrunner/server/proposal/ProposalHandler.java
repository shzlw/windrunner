package com.windrunner.server.proposal;

import com.windrunner.server.user.domain.AppUser;

/** Domain-specific validation and application for one proposal entity type. */
public interface ProposalHandler<T> {
    String entityType();

    void authorize(T change, AppUser actor);

    ProposalPreparedChange prepare(T change, AppUser actor);

    void apply(T change, ProposalPreparedChange prepared, AppUser actor);
}
