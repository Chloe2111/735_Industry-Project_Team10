package com.vcnity.backend.findings;

import com.vcnity.backend.security.CodedFinding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = FindingController.class,
        properties = "findings.api.enabled=true"
)
class FindingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FindingRepository repository;

    private CodedFinding example() {
        return new CodedFinding(
                "synthetic_finding_001",
                "synthetic_transcript_001",
                null,
                "Transport access",
                "We need affordable transport to attend community workshops.",
                0.95,
                2,
                List.of()
        );
    }

    @Test
    void returnsFindingWithExactlyEightContractFields() throws Exception {
        CodedFinding finding = example();

        when(repository.findByItemId(finding.itemId()))
                .thenReturn(Optional.of(finding));

        mvc.perform(get("/api/findings/{itemId}", finding.itemId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andExpect(jsonPath("$.itemId").value(finding.itemId()))
                .andExpect(jsonPath("$.sourceRef").value(finding.sourceRef()))
                .andExpect(jsonPath("$.speakerCode").value(nullValue()))
                .andExpect(jsonPath("$.theme").value(finding.theme()))
                .andExpect(jsonPath("$.quote").value(finding.quote()))
                .andExpect(jsonPath("$.confidence").value(0.95))
                .andExpect(jsonPath("$.tier").value(2))
                .andExpect(jsonPath("$.flags").isEmpty());
    }

    @Test
    void missingFindingReturns404() throws Exception {
        when(repository.findByItemId("missing"))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/findings/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void sourceQueryUsesDefaultPagination() throws Exception {
        CodedFinding finding = example();

        when(repository.findBySourceRef(finding.sourceRef(), 0, 20))
                .thenReturn(new FindingRepository.FindingPage(
                        List.of(finding), 0, 20, false
                ));

        mvc.perform(get("/api/findings")
                        .param("sourceRef", finding.sourceRef()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].itemId")
                        .value(finding.itemId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(repository).findBySourceRef(finding.sourceRef(), 0, 20);
    }

    @Test
    void explicitPaginationIsPassedToRepository() throws Exception {
        when(repository.findBySourceRef("synthetic_source", 2, 5))
                .thenReturn(new FindingRepository.FindingPage(
                        List.of(), 2, 5, false
                ));

        mvc.perform(get("/api/findings")
                        .param("sourceRef", "synthetic_source")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(repository).findBySourceRef("synthetic_source", 2, 5);
    }

    @Test
    void missingSourceReferenceReturns400() throws Exception {
        mvc.perform(get("/api/findings"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
    }

    @Test
    void nonNumericPageReturns400() throws Exception {
        mvc.perform(get("/api/findings")
                        .param("sourceRef", "synthetic_source")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
    }

    @Test
    void repositoryValidationFailureReturnsControlled400() throws Exception {
        when(repository.findBySourceRef("synthetic_source", -1, 20))
                .thenThrow(new IllegalArgumentException(
                        "Internal validation details"
                ));

        mvc.perform(get("/api/findings")
                        .param("sourceRef", "synthetic_source")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"code\":\"INVALID_FINDING_REQUEST\"}"
                ));
    }

    @Test
    void unavailableDatabaseReturnsControlled503() throws Exception {
        when(repository.findByItemId("synthetic_finding_001"))
                .thenThrow(new DataAccessResourceFailureException(
                        "Private database connection details"
                ));

        mvc.perform(get("/api/findings/synthetic_finding_001"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json(
                        "{\"code\":\"FINDING_STORAGE_UNAVAILABLE\"}"
                ));
    }

    @Test
    void malformedStoredDataReturns500InsteadOf404() throws Exception {
        when(repository.findByItemId("synthetic_finding_001"))
                .thenThrow(new DataIntegrityViolationException(
                        "Private stored document details"
                ));

        mvc.perform(get("/api/findings/synthetic_finding_001"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"code\":\"FINDING_STORAGE_ERROR\"}"
                ));
    }
}