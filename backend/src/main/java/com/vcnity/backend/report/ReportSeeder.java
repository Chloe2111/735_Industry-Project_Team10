package com.vcnity.backend.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Populates the "reports" collection from the bundled seed file on first
 * boot, so the reporting view works out of the box without manual DB setup.
 * Only synthetic data -- the AI pipeline doesn't produce real report
 * content yet, so this is a stand-in the same way the frontend's mock
 * service was before this endpoint existed.
 */
@Component
public class ReportSeeder implements ApplicationRunner {

    private final ReportRepository repository;
    private final ObjectMapper objectMapper;

    public ReportSeeder(ReportRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (repository.count() > 0) {
            return;
        }
        List<Report> seed = objectMapper.readValue(
                new ClassPathResource("reports-seed.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Report.class));
        repository.saveAll(seed);
    }
}
