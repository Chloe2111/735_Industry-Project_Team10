package com.vcnity.backend.intake;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        IntakeFixture app = new IntakeFixture();
        app.classify("elders circle", 3);
        String tampered = "{\"groupIds\":[\"elders circle\"],\"text\":\"Restricted story.\",\"tier\":1}";

        SubmissionReceipt receipt = app.intake.submit(mapper.readValue(tampered, SubmissionRequest.class));

        assertEquals(3, receipt.tier());
        assertEquals(SubmissionStatus.HELD, receipt.status());
    }

    @Test
    void receiptHasExactlyTheFieldsTheFormDisplays() throws Exception {
        IntakeFixture app = new IntakeFixture();
        app.classify("youth group a", 2);

        JsonNode json = mapper.valueToTree(app.submit("Great session.", "youth group a"));

        assertEquals(List.of("groupIds", "id", "message", "status", "submittedAt", "tier"), fieldNames(json));
        assertEquals("PUBLISHED", json.get("status").asText());
        assertEquals(2, json.get("tier").asInt());
        assertEquals("youth group a", json.get("groupIds").get(0).asText());
    }

    @Test
    void tierRequestAndGroupListUseTheFieldNamesTheTierPanelSends() throws Exception {
        IntakeFixture app = new IntakeFixture();
        String firstTime = "{\"groupId\":\"Youth Group A\",\"tier\":2,\"setBy\":\"Mohika\",\"reason\":\"Personal stories\",\"expectedVersion\":null}";

        JsonNode result = mapper.valueToTree(app.intake.classifyGroup(mapper.readValue(firstTime, TierRequest.class)));

        assertEquals(List.of("group", "haltedSubmissionIds", "publishedToRecheck"), fieldNames(result));
        JsonNode group = mapper.valueToTree(app.intake.listGroups()).get(0);
        assertEquals(List.of("groupId", "groupName", "history", "reason", "setAt", "setBy", "tier", "version"), fieldNames(group));
        assertEquals("youth group a", group.get("groupId").asText());
        assertEquals("Youth Group A", group.get("groupName").asText());
        assertEquals(2, group.get("tier").asInt());

        // The panel sends back the version it was shown; that is what makes the next change safe.
        String change = "{\"groupId\":\"Youth Group A\",\"tier\":3,\"setBy\":\"Chloee\",\"reason\":\"Restricted\",\"expectedVersion\":"
                + group.get("version").asLong() + "}";
        TierRequest parsed = mapper.readValue(change, TierRequest.class);
        assertEquals(group.get("version").asLong(), parsed.expectedVersion());
        assertEquals(3, app.intake.classifyGroup(parsed).group().getTier());
    }

    @Test
    void publishedSubmissionCarriesTheTextAndTier() throws Exception {
        IntakeFixture app = new IntakeFixture();
        app.classify("youth group a", 1);
        app.submit("Great session.", "youth group a");

        JsonNode published = mapper.valueToTree(app.intake.listPublished("youth group a")).get(0);

        assertEquals(List.of("groupIds", "id", "submittedAt", "text", "tier"), fieldNames(published));
        assertEquals("Great session.", published.get("text").asText());
    }
}
