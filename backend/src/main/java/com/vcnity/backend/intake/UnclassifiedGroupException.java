package com.vcnity.backend.intake;

import com.vcnity.backend.common.ApiException;

import java.util.List;
import java.util.Map;

/** A submission names a group that no person has classified yet. Nothing is stored. HTTP 422. */
public class UnclassifiedGroupException extends ApiException {

    private final List<String> groupIds;

    public UnclassifiedGroupException(List<String> groupIds) {
        super(422, "No tier has been set for: " + String.join(", ", groupIds)
                + ". A person must classify the group before anything can be submitted for it.");
        this.groupIds = List.copyOf(groupIds);
    }

    public List<String> getGroupIds() {
        return groupIds;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("unclassifiedGroupIds", groupIds);
    }
}
