package com.vcnity.backend.community.repository;

import com.vcnity.backend.community.model.GroupMembership;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository
        extends MongoRepository<GroupMembership, String> {

    List<GroupMembership> findByGroupId(String groupId);

    List<GroupMembership> findByUserId(String userId);

    Optional<GroupMembership> findByGroupIdAndUserId(
            String groupId,
            String userId
    );

    boolean existsByGroupIdAndUserId(
            String groupId,
            String userId
    );
}