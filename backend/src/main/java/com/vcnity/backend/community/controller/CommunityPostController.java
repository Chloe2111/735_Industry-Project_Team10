package com.vcnity.backend.community.controller;

import com.vcnity.backend.community.model.CommunityPost;
import com.vcnity.backend.community.service.CommunityPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityPostController {

    private final CommunityPostService postService;

    public CommunityPostController(
            CommunityPostService postService) {
        this.postService = postService;
    }

    // Create a new community post
    @PostMapping
    public ResponseEntity<CommunityPost> createPost(
            @RequestBody CommunityPost post) {

        if (post.getUserId() == null ||
                post.getUserId().isBlank() ||
                post.getTitle() == null ||
                post.getTitle().isBlank() ||
                post.getContent() == null ||
                post.getContent().isBlank()) {

            return ResponseEntity.badRequest().build();
        }

        return postService.createPost(post)
                .map(createdPost ->
                        ResponseEntity
                                .created(URI.create(
                                        "/api/community/posts/"
                                                + createdPost.getId()))
                                .body(createdPost))
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    // Get overall community activity feed
    @GetMapping
    public ResponseEntity<List<CommunityPost>> getActivityFeed() {
        return ResponseEntity.ok(
                postService.getActivityFeed());
    }

    // Get posts for one community group
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<CommunityPost>> getPostsByGroup(
            @PathVariable String groupId) {

        return ResponseEntity.ok(
                postService.getPostsByGroup(groupId));
    }

    // Get one post
    @GetMapping("/{id}")
    public ResponseEntity<CommunityPost> getPostById(
            @PathVariable String id) {

        return postService.getPostById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    // Delete a post
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String id) {

        if (postService.deletePost(id)) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}