package com.vcnity.backend.community.repository;

import com.vcnity.backend.community.model.CommunityPost;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CommunityPostRepository
        extends MongoRepository<CommunityPost, String> {

    List<CommunityPost> findByGroupIdOrderByCreatedAtDesc(String groupId);

    List<CommunityPost> findAllByOrderByCreatedAtDesc();
}