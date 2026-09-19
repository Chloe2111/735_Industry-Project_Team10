package com.vcnity.backend.community.controller;

import com.vcnity.backend.community.service.CommunityPostService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityPostControllerTest {
    @Test
    void createPostRejectsMissingGroup() throws Exception {
        CommunityPostService service = mock(CommunityPostService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CommunityPostController(service)).build();

        mvc.perform(post("/api/community/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\",\"title\":\"Update\",\"content\":\"Hello\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).createPost(any());
    }
}
