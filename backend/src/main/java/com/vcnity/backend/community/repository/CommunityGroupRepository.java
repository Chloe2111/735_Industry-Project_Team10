package com.vcnity.backend.community.repository;

import com.vcnity.backend.community.model.CommunityGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommunityGroupRepository
        extends MongoRepository<CommunityGroup, String> {
}