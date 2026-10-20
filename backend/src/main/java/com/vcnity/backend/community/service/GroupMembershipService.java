package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.GroupMembership;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import com.vcnity.backend.community.repository.GroupMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GroupMembershipService {

    private final GroupMembershipRepository membershipRepository;
    private final CommunityGroupRepository groupRepository;

    public GroupMembershipService(
            GroupMembershipRepository membershipRepository,
            CommunityGroupRepository groupRepository) {
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
    }

    // Join a community group
    public Optional<GroupMembership> joinGroup(
            String groupId,
            String userId) {

        // Group must exist
        if (!groupRepository.existsById(groupId)) {
            return Optional.empty();
        }

        // Prevent duplicate membership
        Optional<GroupMembership> existingMembership =
                membershipRepository.findByGroupIdAndUserId(groupId, userId);

        if (existingMembership.isPresent()) {
            return existingMembership;
        }

        GroupMembership membership =
                new GroupMembership(groupId, userId);

        return Optional.of(membershipRepository.save(membership));
    }

    // Get all members of a group
    public List<GroupMembership> getGroupMembers(String groupId) {
        return membershipRepository.findByGroupId(groupId);
    }

    // Get all groups joined by a user
    public List<GroupMembership> getUserMemberships(String userId) {
        return membershipRepository.findByUserId(userId);
    }

    // Leave a community group
    public boolean leaveGroup(
            String groupId,
            String userId) {

        Optional<GroupMembership> membership =
                membershipRepository.findByGroupIdAndUserId(groupId, userId);

        if (membership.isEmpty()) {
            return false;
        }

        membershipRepository.delete(membership.get());
        return true;
    }
}