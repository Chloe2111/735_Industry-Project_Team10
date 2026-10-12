package com.vcnity.backend.findings;

import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import com.vcnity.backend.security.BatchCodingWorkflow;
import com.vcnity.backend.security.CodedFinding;
import com.vcnity.backend.security.CodingDraft;
import com.vcnity.backend.security.CodingSource;
import com.vcnity.backend.security.TranscriptCoder;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/"
                + "vcnity_story24_dev?serverSelectionTimeoutMS=5000",
        "spring.data.mongodb.database=vcnity_story24_dev",
        "findings.api.enabled=true"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(
        named = "mongodb.integration",
        matches = "true"
)
class FindingApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoFindingRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ExceptionsQueueService queue;

    @Test
    void batchFindingsAreStoredAndRetrievedThroughApi()
            throws Exception {

        assertEquals(
                "vcnity_story24_dev",
                mongoTemplate.getDb().getName()
        );
        repository.ensureIndexes();

        String sourceRef = "synthetic-api-" + UUID.randomUUID();

        String text =
                "We need affordable transport to attend community workshops.";

        CodingSource source = new CodingSource(
                sourceRef,
                text,
                2,
                "PERSON_001"
        );

        // Controlled outputs: one grounded quote and one invented quote.
        TranscriptCoder coder = input -> List.of(
                new CodingDraft("Transport access", input, 0.95),
                new CodingDraft(
                        "Workshop facilities",
                        "The venue provides free childcare.",
                        0.95
                )
        );

        PersistedBatchCodingWorkflow workflow =
                new PersistedBatchCodingWorkflow(
                        new BatchCodingWorkflow(coder, queue),
                        repository
                );

        try {
            PersistedBatchCodingWorkflow.Result result =
                    workflow.run(List.of(source));

            assertTrue(result.allProducedFindingsPersisted());
            assertEquals(2, result.persistenceReceipts().size());

            BatchCodingWorkflow.RoutedSource routed =
                    result.workflowResult().sources().get(0);

            assertEquals(
                    BatchCodingWorkflow.RoutingStatus.PENDING_EXCEPTION_REVIEW,
                    routed.routingStatus()
            );

            assertFalse(routed.queueReceipts().isEmpty());
            assertTrue(routed.queueReceipts().stream().allMatch(
                    receipt -> receipt.receipt().complete()
            ));

            List<CodedFinding> findings =
                    routed.codingOutcome().findings();

            assertEquals(2, findings.size());
            assertTrue(findings.stream().anyMatch(
                    finding -> finding.flags().isEmpty()
            ));
            assertTrue(findings.stream().anyMatch(
                    finding -> finding.flags().contains("quoteNotGrounded")
            ));

            for (CodedFinding finding : findings) {
                // Read independently from MongoDB before checking the API.
                assertEquals(
                        finding,
                        repository.findByItemId(
                                finding.itemId()
                        ).orElseThrow()
                );

                mvc.perform(get(
                                "/api/findings/{itemId}",
                                finding.itemId()
                        ))
                        .andExpect(status().isOk())
                        .andExpect(header().string(
                                "Cache-Control", "no-store"
                        ))
                        .andExpect(jsonPath("$", aMapWithSize(8)))
                        .andExpect(jsonPath("$.itemId")
                                .value(finding.itemId()))
                        .andExpect(jsonPath("$.sourceRef")
                                .value(sourceRef))
                        .andExpect(jsonPath("$.speakerCode")
                                .value("PERSON_001"))
                        .andExpect(jsonPath("$.theme")
                                .value(finding.theme()))
                        .andExpect(jsonPath("$.quote")
                                .value(finding.quote()))
                        .andExpect(jsonPath("$.confidence")
                                .value(finding.confidence()))
                        .andExpect(jsonPath("$.tier").value(2))
                        .andExpect(jsonPath("$.flags")
                                .value(org.hamcrest.Matchers.equalTo(finding.flags())));
            }

            mvc.perform(get("/api/findings")
                            .param("sourceRef", sourceRef)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings.length()").value(2))
                    .andExpect(jsonPath(
                            "$.findings[*].itemId",
                            containsInAnyOrder(
                                    findings.get(0).itemId(),
                                    findings.get(1).itemId()
                            )
                    ))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.hasNext").value(false));

        } finally {
            // Remove only records created for this unique test source.
            mongoTemplate.execute(
                    MongoFindingRepository.COLLECTION,
                    collection -> collection.deleteMany(
                            new Document("sourceRef", sourceRef)
                    )
            );
        }
    }
}