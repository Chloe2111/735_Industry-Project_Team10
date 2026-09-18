package com.vcnity.backend.community.controller;

import com.vcnity.backend.community.model.GroupMembership;
import com.vcnity.backend.community.service.GroupMembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
public class GroupMembershipController {

    private final GroupMembershipService membershipService;

    public GroupMembershipController(
            GroupMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    // Join a community group
    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<GroupMembership> joinGroup(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request) {

        String userId = request.get("userId");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return membershipService.joinGroup(groupId, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get all members of a group
    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<List<GroupMembership>> getGroupMembers(
            @PathVariable String groupId) {

        return ResponseEntity.ok(
                membershipService.getGroupMembers(groupId));
    }

    // Get all memberships for a user
    @GetMapping("/users/{userId}/memberships")
    public ResponseEntity<List<GroupMembership>> getUserMemberships(
            @PathVariable String userId) {

        return ResponseEntity.ok(
                membershipService.getUserMemberships(userId));
    }

    // Leave a community group
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable String groupId,
            @PathVariable String userId) {

        if (membershipService.leaveGroup(groupId, userId)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}