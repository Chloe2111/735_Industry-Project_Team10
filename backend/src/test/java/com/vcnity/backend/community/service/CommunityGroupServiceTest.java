package com.vcnity.backend.community.service;

import com.vcnity.backend.community.model.CommunityGroup;
import com.vcnity.backend.community.repository.CommunityGroupRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommunityGroupServiceTest {
    @Test
    void createGroupSetsServerMetadataAndSaves() {
        CommunityGroupRepository repository = mock(CommunityGroupRepository.class);
        when(repository.save(any(CommunityGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CommunityGroupService service = new CommunityGroupService(repository);
        CommunityGroup input = new CommunityGroup();
        input.setId("client-id");
        input.setName("  Local Group  ");
        input.setDescription("  Local projects  ");
        input.setCategory(" Community ");
        input.setCreatedBy("user-1");

        CommunityGroup saved = service.createGroup(input);

        assertNull(saved.getId());
        assertEquals("Local Group", saved.getName());
        assertEquals("Local projects", saved.getDescription());
        assertEquals("Community", saved.getCategory());
        assertNotNull(saved.getCreatedAt());
        verify(repository).save(input);
    }
}
