package com.vcnity.backend.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcnity.backend.community.model.CommunityGroup;
import com.vcnity.backend.community.service.CommunityGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityGroupControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createGroupRejectsBlankRequiredFields() throws Exception {
        CommunityGroupService service = mock(CommunityGroupService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CommunityGroupController(service)).build();
        CommunityGroup invalid = new CommunityGroup();
        invalid.setName(" "); invalid.setDescription(""); invalid.setCategory("");

        mvc.perform(post("/api/community/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        verify(service, never()).createGroup(any());
    }

    @Test
    void createGroupReturnsCreatedForValidInput() throws Exception {
        CommunityGroupService service = mock(CommunityGroupService.class);
        CommunityGroup saved = new CommunityGroup("Local Group", "Projects", "Community", "u1");
        saved.setId("g1");
        when(service.createGroup(any())).thenReturn(saved);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CommunityGroupController(service)).build();

        mvc.perform(post("/api/community/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Local Group\",\"description\":\"Projects\",\"category\":\"Community\",\"createdBy\":\"u1\"}"))
                .andExpect(status().isCreated());
    }
}
