package com.vcnity.backend.report;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    private Report listOnlyReport() {
        Report report = new Report();
        report.setId("youth-space-ideas");
        report.setProject("Youth Space Ideas");
        report.setGroup("[Group 4]");
        report.setReportingPeriod("In progress");
        report.setStatus("DRAFT");
        return report;
    }

    private Report fullReport() {
        Report report = listOnlyReport();
        report.setId("skate-park-design-consultation");
        report.setProject("Skate Park Design Consultation");
        report.setStatus("FINAL");
        report.setTitle("Skate Park Design Consultation");
        report.setPurpose("Test purpose.");
        return report;
    }

    @Test
    void listReturns200WithAllReports() throws Exception {
        when(reportService.findAll()).thenReturn(List.of(fullReport(), listOnlyReport()));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].project").value("Skate Park Design Consultation"));
    }

    @Test
    void getByKnownIdReturns200WithFullDetail() throws Exception {
        when(reportService.findDetailById("skate-park-design-consultation")).thenReturn(Optional.of(fullReport()));

        mockMvc.perform(get("/api/reports/skate-park-design-consultation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Skate Park Design Consultation"))
                .andExpect(jsonPath("$.data.purpose").value("Test purpose."));
    }

    @Test
    void getByUnknownIdReturns404() throws Exception {
        when(reportService.findDetailById("does-not-exist")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/reports/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Report not found."));
    }

    @Test
    void getByIdWithNoDetailContentReturns404() throws Exception {
        // Listed (e.g. DRAFT) but no detail content yet -- ReportService already
        // filters this out, so the controller just needs to handle the empty Optional.
        when(reportService.findDetailById("youth-space-ideas")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/reports/youth-space-ideas"))
                .andExpect(status().isNotFound());
    }
}
