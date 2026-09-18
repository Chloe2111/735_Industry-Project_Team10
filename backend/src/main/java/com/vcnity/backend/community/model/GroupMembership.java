package com.vcnity.backend.community.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "group_memberships")
public class GroupMembership {

    @Id
    private String id;

    private String groupId;
    private String userId;
    private LocalDateTime joinedAt;

    public GroupMembership() {
    }

    public GroupMembership(String groupId, String userId) {
        this.groupId = groupId;
        this.userId = userId;
        this.joinedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}