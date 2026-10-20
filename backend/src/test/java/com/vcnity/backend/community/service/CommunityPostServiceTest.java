package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.CommunityPost;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import com.vcnity.backend.community.repository.CommunityPostRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommunityPostServiceTest {
    @Test
    void createPostSavesForExistingGroup() {
        CommunityPostRepository posts = mock(CommunityPostRepository.class);
        CommunityGroupRepository groups = mock(CommunityGroupRepository.class);
        when(groups.existsById("g1")).thenReturn(true);
        when(posts.save(any(CommunityPost.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CommunityPostService service = new CommunityPostService(posts, groups);
        CommunityPost post = new CommunityPost();
        post.setGroupId("g1"); post.setUserId("u1"); post.setTitle("Update"); post.setContent("Hello");

        CommunityPost saved = service.createPost(post).orElseThrow();

        assertNotNull(saved.getCreatedAt());
        verify(posts).save(post);
    }

    @Test
    void createPostRejectsUnknownGroup() {
        CommunityPostRepository posts = mock(CommunityPostRepository.class);
        CommunityGroupRepository groups = mock(CommunityGroupRepository.class);
        when(groups.existsById("missing")).thenReturn(false);
        CommunityPostService service = new CommunityPostService(posts, groups);
        CommunityPost post = new CommunityPost(); post.setGroupId("missing");

        assertTrue(service.createPost(post).isEmpty());
        verify(posts, never()).save(any());
    }
}
