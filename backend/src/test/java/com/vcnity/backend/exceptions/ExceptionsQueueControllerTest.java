package com.vcnity.backend.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcnity.backend.exceptions.controller.ExceptionsQueueController;
import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExceptionsQueueController.class)
class ExceptionsQueueControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ExceptionsQueueService service;
    private ExceptionItem item;

    @BeforeEach void setup() {
        item = new ExceptionItem("EX-1", FlagType.QUOTE_NOT_FOUND, "bad quote", "source", 0.8, "SRC-1");
    }

    @Test void listsQueue() throws Exception {
        when(service.list()).thenReturn(List.of(item));
        mvc.perform(get("/api/exceptions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flagType").value("QUOTE_NOT_FOUND"));
    }

    @Test void getsAndReviewsItem() throws Exception {
        when(service.get("EX-1")).thenReturn(Optional.of(item));
        when(service.clear(eq("EX-1"), anyString())).thenReturn(Optional.of(item));
        mvc.perform(get("/api/exceptions/EX-1")).andExpect(status().isOk());
        mvc.perform(patch("/api/exceptions/EX-1/clear").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"checked\"}"))
                .andExpect(status().isOk());
    }

    @Test void returns404ForUnknownItem() throws Exception {
        when(service.get("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/exceptions/missing")).andExpect(status().isNotFound());
    }
}
