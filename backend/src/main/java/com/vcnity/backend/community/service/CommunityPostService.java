package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.CommunityPost;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import com.vcnity.backend.community.repository.CommunityPostRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityGroupRepository groupRepository;

    public CommunityPostService(
            CommunityPostRepository postRepository,
            CommunityGroupRepository groupRepository) {

        this.postRepository = postRepository;
        this.groupRepository = groupRepository;
    }

    // Create a post inside an existing community group
    public Optional<CommunityPost> createPost(CommunityPost post) {

        if (post.getGroupId() == null ||
                !groupRepository.existsById(post.getGroupId())) {
            return Optional.empty();
        }

        post.setCreatedAt(LocalDateTime.now());

        return Optional.of(postRepository.save(post));
    }

    // Get the overall community activity feed
    public List<CommunityPost> getActivityFeed() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    // Get posts belonging to one group
    public List<CommunityPost> getPostsByGroup(String groupId) {
        return postRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    // Get one post
    public Optional<CommunityPost> getPostById(String id) {
        return postRepository.findById(id);
    }

    // Delete a post
    public boolean deletePost(String id) {

        if (!postRepository.existsById(id)) {
            return false;
        }

        postRepository.deleteById(id);
        return true;
    }
}