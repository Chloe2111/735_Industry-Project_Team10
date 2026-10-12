package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.GroupMembership;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import com.vcnity.backend.community.repository.GroupMembershipRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupMembershipServiceTest {
    @Test
    void joinGroupCreatesMembershipWhenGroupExists() {
        GroupMembershipRepository memberships = mock(GroupMembershipRepository.class);
        CommunityGroupRepository groups = mock(CommunityGroupRepository.class);
        when(groups.existsById("g1")).thenReturn(true);
        when(memberships.findByGroupIdAndUserId("g1", "u1")).thenReturn(Optional.empty());
        when(memberships.save(any(GroupMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        GroupMembershipService service = new GroupMembershipService(memberships, groups);

        GroupMembership result = service.joinGroup("g1", "u1").orElseThrow();

        assertEquals("g1", result.getGroupId());
        assertEquals("u1", result.getUserId());
        assertNotNull(result.getJoinedAt());
    }

    @Test
    void duplicateJoinIsIdempotent() {
        GroupMembershipRepository memberships = mock(GroupMembershipRepository.class);
        CommunityGroupRepository groups = mock(CommunityGroupRepository.class);
        GroupMembership existing = new GroupMembership("g1", "u1");
        when(groups.existsById("g1")).thenReturn(true);
        when(memberships.findByGroupIdAndUserId("g1", "u1")).thenReturn(Optional.of(existing));
        GroupMembershipService service = new GroupMembershipService(memberships, groups);

        assertSame(existing, service.joinGroup("g1", "u1").orElseThrow());
        verify(memberships, never()).save(any());
    }
}
