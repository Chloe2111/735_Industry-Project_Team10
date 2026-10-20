package com.vcnity.backend.intake;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.model.ReviewStatus;
import com.vcnity.backend.exceptions.service.ReviewBlockedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The HTTP side of PATCH /api/exceptions/{id}/code, with the service mocked. */
@WebMvcTest(FacilitatorCodingController.class)
class FacilitatorCodingControllerTest {

    @Autowired MockMvc mvc;
    @MockBean FacilitatorCodingService service;

    private static final String BODY = "{\"theme\":\"Lighting\",\"quote\":\"More lights would help.\",\"note\":\"ok\"}";

    @Test
    void returnsTheClearedItem() throws Exception {
        ExceptionItem item = new ExceptionItem("EX-1", FlagType.AI_COULD_NOT_CODE, "idea", "context", null, "S-1");
        item.setStatus(ReviewStatus.CLEARED);
        when(service.code("EX-1", "Lighting", "More lights would help.", "ok")).thenReturn(Optional.of(item));

        mvc.perform(patch("/api/exceptions/EX-1/code").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"));
        verify(service).code("EX-1", "Lighting", "More lights would help.", "ok");
    }

    @Test
    void unknownItemIs404() throws Exception {
        when(service.code(any(), any(), any(), any())).thenReturn(Optional.empty());
        mvc.perform(patch("/api/exceptions/missing/code").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void aQuoteNotInTheIdeaIs400WithAMessage() throws Exception {
        when(service.code(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("The quote must be copied exactly from the idea text."));
        mvc.perform(patch("/api/exceptions/EX-1/code").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The quote must be copied exactly from the idea text."));
    }

    @Test
    void aBlockedItemIs409() throws Exception {
        when(service.code(any(), any(), any(), any()))
                .thenThrow(new ReviewBlockedException("This idea has already been reviewed."));
        mvc.perform(patch("/api/exceptions/EX-1/code").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }
}
