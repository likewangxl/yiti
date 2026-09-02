package com.bank.branch.platform.redengine.api.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReTaskSubmissionStatusTest {

    @Test
    void draftIsRepresentedBecauseTheImplementedTableAllowsIt() {
        assertNotNull(ReTaskSubmissionStatus.valueOf("DRAFT"));
        assertTrue(ReTaskSubmissionStatus.DRAFT.canTransitionTo(ReTaskSubmissionStatus.BRANCH_PENDING));
    }

    @Test
    void branchApprovalAndSubmitToOrganizationAreSeparateTransitions() {
        assertTrue(ReTaskSubmissionStatus.BRANCH_PENDING
                .canTransitionTo(ReTaskSubmissionStatus.BRANCH_APPROVED));
        assertFalse(ReTaskSubmissionStatus.BRANCH_PENDING
                .canTransitionTo(ReTaskSubmissionStatus.ORG_PENDING));
        assertTrue(ReTaskSubmissionStatus.BRANCH_APPROVED
                .canTransitionTo(ReTaskSubmissionStatus.ORG_PENDING));
    }

    @Test
    void bothReviewersCanRejectBackToReporter() {
        assertTrue(ReTaskSubmissionStatus.BRANCH_PENDING
                .canTransitionTo(ReTaskSubmissionStatus.REJECTED_BY_BRANCH));
        assertTrue(ReTaskSubmissionStatus.ORG_PENDING
                .canTransitionTo(ReTaskSubmissionStatus.REJECTED_BY_ORG));
    }

    @Test
    void rejectedSubmissionCanBeResubmittedThroughBranchReview() {
        assertTrue(ReTaskSubmissionStatus.REJECTED_BY_BRANCH
                .canTransitionTo(ReTaskSubmissionStatus.BRANCH_PENDING));
        assertTrue(ReTaskSubmissionStatus.REJECTED_BY_ORG
                .canTransitionTo(ReTaskSubmissionStatus.BRANCH_PENDING));
        assertFalse(ReTaskSubmissionStatus.APPROVED
                .canTransitionTo(ReTaskSubmissionStatus.BRANCH_PENDING));
    }
}
