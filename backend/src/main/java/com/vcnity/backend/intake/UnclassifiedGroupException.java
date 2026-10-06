package com.vcnity.backend.intake;

import java.util.List;

/** Thrown when a submission names a group that no person has classified yet. Nothing is stored. */
public class UnclassifiedGroupException extends RuntimeException {

    private final List<String> groupIds;

    public UnclassifiedGroupException(List<String> groupIds) {
        super("No tier has been set for: " + String.join(", ", groupIds)
                + ". A person must classify the group before anything can be submitted for it.");
        this.groupIds = List.copyOf(groupIds);
    }

    public List<String> getGroupIds() {
        return groupIds;
    }
}
