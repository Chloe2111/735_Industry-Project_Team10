package com.vcnity.backend.intake;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub Task 24.1: the JSON the submission form sends matches what the intake endpoint expects,
 * and the JSON it gets back has the fields the form displays.
 *
 * The frontend side of the same contract is pinned in
 * frontend/src/pages/__tests__/CommunitySubmissionPage.test.jsx.
 */
class IntakePayloadContractTest {

    // Same lenient setting Spring Boot uses for request bodies: unknown properties are ignored.
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private IntakeService newIntake() {
        return new IntakeService(new GroupTierService(new InMemoryGroupTierStore()), new InMemorySubmissionStore(),
                new RecordingPipeline(), new ExceptionsQueueService());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return new ArrayList<>(new TreeSet<>(names));
    }

    @Test
    void formPayloadIsReadIntoTheRequestTheEndpointExpects() throws Exception {
        String formPayload = "{\"groupIds\":[\"Youth Group A\"],\"text\":\"The workshop was well organised.\"}";

        SubmissionRequest request = mapper.readValue(formPayload, SubmissionRequest.class);

        assertEquals(List.of("Youth Group A"), request.groupIds());
        assertEquals("The workshop was well organised.", request.text());
    }

    @Test
    void aTierSentByTheClientIsIgnoredSoItCanNeverLowerTheGroupsTier() throws Exception {
        IntakeService intake = newIntake();
        intake.classifyGroup(new TierRequest("elders circle", null, 3, "Mohika", "Cultural knowledge"));
        String tampered = "{\"groupIds\":[\"elders circle\"],\"text\":\"Restricted story.\",\"tier\":1}";

        SubmissionReceipt receipt = intake.submit(mapper.readValue(tampered, SubmissionRequest.class));

        assertEquals(3, receipt.tier());
        assertEquals(SubmissionStatus.HELD, receipt.status());
    }

    @Test
    void receiptHasExactlyTheFieldsTheFormDisplays() throws Exception {
        IntakeService intake = newIntake();
        intake.classifyGroup(new TierRequest("youth group a", null, 2, "Mohika", "Personal stories"));

        JsonNode json = mapper.valueToTree(
                intake.submit(new SubmissionRequest(List.of("youth group a"), "Great session.")));

        assertEquals(List.of("groupIds", "id", "message", "status", "submittedAt", "tier", "tierLabel"), fieldNames(json));
        assertEquals("PUBLISHED", json.get("status").asText());
        assertEquals(2, json.get("tier").asInt());
        assertEquals("Personal or sensitive", json.get("tierLabel").asText());
        assertEquals("youth group a", json.get("groupIds").get(0).asText());
    }

    @Test
    void tierRequestAndGroupListUseTheFieldNamesTheTierPanelSends() throws Exception {
        String panelPayload = "{\"groupId\":\"Youth Group A\",\"tier\":2,\"setBy\":\"Mohika\",\"reason\":\"Personal stories\"}";
        IntakeService intake = newIntake();

        JsonNode result = mapper.valueToTree(intake.classifyGroup(mapper.readValue(panelPayload, TierRequest.class)));

        assertEquals(List.of("group", "haltedSubmissionIds", "publishedToRecheck"), fieldNames(result));
        JsonNode group = mapper.valueToTree(intake.listGroups()).get(0);
        assertEquals(List.of("groupId", "groupName", "history", "reason", "setAt", "setBy", "tier"), fieldNames(group));
        assertEquals("youth group a", group.get("groupId").asText());
        assertEquals("Youth Group A", group.get("groupName").asText());
        assertEquals(2, group.get("tier").asInt());
    }

    @Test
    void publishedSubmissionCarriesTheTextAndTier() throws Exception {
        IntakeService intake = newIntake();
        intake.classifyGroup(new TierRequest("youth group a", null, 1, "Mohika", "General"));
        intake.submit(new SubmissionRequest(List.of("youth group a"), "Great session."));

        JsonNode published = mapper.valueToTree(intake.listPublished("youth group a")).get(0);

        assertEquals(List.of("groupIds", "id", "submittedAt", "text", "tier"), fieldNames(published));
        assertEquals("Great session.", published.get("text").asText());
    }
}
