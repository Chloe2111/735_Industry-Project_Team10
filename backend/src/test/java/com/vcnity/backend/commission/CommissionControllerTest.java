package com.vcnity.backend.commission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommissionController.class)
class CommissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommissionService commissionService;

    // No "tier" key: the client no longer picks one (VCNITY's AI is meant
    // to classify it later), so a request without it must still succeed.
    private Map<String, Object> validRequestBody() {
        return Map.of(
                "title", "Skate Park Design Consultation",
                "description", "What do you need feedback on?",
                "incentive", 3200,
                "deadline", "12 weeks from posting",
                "groups", List.of("Youth groups"),
                "reportFormats", List.of("Executive summary"));
    }

    @Test
    void validRequestWithNoTierReturns201WithPendingTier() throws Exception {
        Commission saved = new Commission(
                "Skate Park Design Consultation",
                "What do you need feedback on?",
                3200,
                "12 weeks from posting",
                null,
                List.of("Youth groups"),
                List.of("Executive summary"));
        when(commissionService.create(any())).thenReturn(saved);

        mockMvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Skate Park Design Consultation"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.tier").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void blankTitleReturns400WithFieldError() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("title", "  ");

        mockMvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.title").exists());

        verify(commissionService, never()).create(any());
    }

    @Test
    void nonPositiveIncentiveReturns400WithFieldError() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("incentive", 0);

        mockMvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.incentive").exists());

        verify(commissionService, never()).create(any());
    }

    @Test
    void emptyGroupsReturns400WithFieldError() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("groups", List.of());

        mockMvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.groups").exists());
    }

    @Test
    void invalidTierReturns400WithFieldError() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(validRequestBody());
        body.put("tier", "Tier 9");
        when(commissionService.create(any()))
                .thenThrow(new InvalidCommissionFieldException("tier", "Tier must be one of: Tier 1, Tier 2, Tier 3."));

        mockMvc.perform(post("/api/commissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tier").exists());
    }
}
