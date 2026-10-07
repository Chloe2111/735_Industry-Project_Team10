package com.vcnity.backend.intake;

/**
 * A person's decision to set or change a group's tier.
 *
 * expectedVersion is the "version" of the group record the person was looking at when they decided
 * (from GET /api/intake/group-tiers), or null if they believe the group has not been classified yet.
 * If the record has changed since, the request is refused with 409 rather than overwriting it.
 * groupName is optional display text.
 */
public record TierRequest(String groupId, String groupName, Integer tier, String setBy, String reason,
                          Long expectedVersion) {
}
