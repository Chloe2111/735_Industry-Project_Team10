package com.vcnity.backend.intake;

/** A person's decision to set or change a group's tier. groupName is optional display text. */
public record TierRequest(String groupId, String groupName, Integer tier, String setBy, String reason) {
}
