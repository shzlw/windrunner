package com.windrunner.server.proposal;

import com.windrunner.server.user.domain.AppUser;

/**
 * Domain-specific validation and application for one proposal entity type.
 */
public interface ProposalHandler<T, P> {
    String getEntityType();

    void authorize(T change, AppUser actor);

    P prepare(T change, AppUser actor);

    void apply(T change, P prepared, AppUser actor);

    T buildRevert(ProposalChange appliedChange, AppUser actor);
}
