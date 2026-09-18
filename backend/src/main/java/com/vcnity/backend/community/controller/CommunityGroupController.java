package com.vcnity.backend.community.controller;

import com.vcnity.backend.community.model.CommunityGroup;
import com.vcnity.backend.community.service.CommunityGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/community/groups")
public class CommunityGroupController {

    private final CommunityGroupService groupService;

    public CommunityGroupController(CommunityGroupService groupService) {
        this.groupService = groupService;
    }

    // Create a new community group
    @PostMapping
    public ResponseEntity<CommunityGroup> createGroup(
            @RequestBody CommunityGroup group) {

        CommunityGroup createdGroup = groupService.createGroup(group);

        return ResponseEntity
                .created(URI.create("/api/community/groups/" + createdGroup.getId()))
                .body(createdGroup);
    }

    // Get all community groups
    @GetMapping
    public ResponseEntity<List<CommunityGroup>> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    // Get one community group
    @GetMapping("/{id}")
    public ResponseEntity<CommunityGroup> getGroupById(
            @PathVariable String id) {

        return groupService.getGroupById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update a community group
    @PutMapping("/{id}")
    public ResponseEntity<CommunityGroup> updateGroup(
            @PathVariable String id,
            @RequestBody CommunityGroup group) {

        return groupService.updateGroup(id, group)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Delete a community group
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable String id) {

        if (groupService.deleteGroup(id)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}