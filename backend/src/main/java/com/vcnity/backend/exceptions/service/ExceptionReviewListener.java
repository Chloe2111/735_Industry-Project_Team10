package com.vcnity.backend.exceptions.service;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.ReviewStatus;

/**
 * Lets another feature react when a reviewer clears or rejects a queue item,
 * without the queue knowing anything about that feature.
 *
 * The queue is shared: it holds community submissions and Story 18 coding findings.
 * A listener is called for EVERY reviewed item, so it must check
 * {@link ExceptionItem#getSourceType()} and return immediately for items it did not raise.
 */
@FunctionalInterface
public interface ExceptionReviewListener {

    /**
     * Called before the new status is stored on the item.
     *
     * @param item      the item being reviewed; its status is still the old one
     * @param newStatus the status about to be stored (CLEARED or REJECTED)
     * @throws RuntimeException to stop the review; the item then keeps its old status
     */
    void beforeReview(ExceptionItem item, ReviewStatus newStatus);
}
