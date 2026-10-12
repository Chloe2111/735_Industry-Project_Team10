package com.vcnity.backend.findings;

import com.mongodb.WriteConcern;
import com.vcnity.backend.security.CodedFinding;
import org.bson.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoFindingRepository implements FindingRepository {

    public static final String COLLECTION = "findings";

    private final MongoTemplate mongoTemplate;

    public MongoFindingRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(
                mongoTemplate,
                "MongoTemplate must not be null"
        );
    }

    /**
     * Explicit setup operation, called before integration tests
     * and when configuring finding persistence.
     *
     * It does not run automatically during application startup.
     */
    public void ensureIndexes() {
        mongoTemplate.execute(COLLECTION, collection ->
                collection.createIndex(
                        new Document("sourceRef", 1)
                                .append("itemId", 1)
                )
        );
    }

    @Override
    public CodedFinding save(CodedFinding finding) {
        if (finding == null) {
            throw new IllegalArgumentException(
                    "Finding must not be null"
            );
        }

        Document document = toDocument(finding);

        try {
            mongoTemplate.execute(COLLECTION, collection -> {
                collection.withWriteConcern(WriteConcern.ACKNOWLEDGED)
                        .insertOne(document);
                return Boolean.TRUE;
            });

            return finding;
        } catch (DuplicateKeyException exception) {
            Optional<CodedFinding> existing =
                    findByItemId(finding.itemId());

            if (existing.isPresent()
                    && existing.get().equals(finding)) {
                return existing.get();
            }

            throw new FindingConflictException();
        }
    }

    @Override
    public Optional<CodedFinding> findByItemId(String itemId) {
        requireText(itemId, "itemId");

        Document document = mongoTemplate.execute(
                COLLECTION,
                collection -> collection.find(
                        new Document("_id", itemId)
                ).first()
        );

        return Optional.ofNullable(document)
                .map(this::fromDocument);
    }

    @Override
    public FindingPage findBySourceRef(
            String sourceRef,
            int page,
            int size
    ) {
        requireText(sourceRef, "sourceRef");

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be nonnegative and size must be 1 to 100"
            );
        }

        long offset = (long) page * size;

        List<Document> stages = new ArrayList<>();
        stages.add(new Document(
                "$match",
                new Document("sourceRef", sourceRef)
        ));
        stages.add(new Document(
                "$sort",
                new Document("itemId", 1)
        ));

        if (offset > 0) {
            stages.add(new Document("$skip", offset));
        }

        // Fetch one extra record to determine whether another page exists.
        stages.add(new Document("$limit", size + 1));

        List<Document> documents = mongoTemplate.execute(
                COLLECTION,
                collection -> collection.aggregate(stages)
                        .into(new ArrayList<Document>())
        );

        if (documents == null) {
            throw new IllegalStateException(
                    "Database query returned no result container"
            );
        }

        boolean hasNext = documents.size() > size;

        List<CodedFinding> findings = documents.stream()
                .limit(size)
                .map(this::fromDocument)
                .toList();

        return new FindingPage(findings, page, size, hasNext);
    }

    private Document toDocument(CodedFinding finding) {
        return new Document("_id", finding.itemId())
                .append("itemId", finding.itemId())
                .append("sourceRef", finding.sourceRef())
                .append("speakerCode", finding.speakerCode())
                .append("theme", finding.theme())
                .append("quote", finding.quote())
                .append("confidence", finding.confidence())
                .append("tier", finding.tier())
                .append("flags", new ArrayList<>(finding.flags()));
    }

    private CodedFinding fromDocument(Document document) {
        try {
            List<String> requiredFields = List.of(
                    "itemId",
                    "sourceRef",
                    "speakerCode",
                    "theme",
                    "quote",
                    "confidence",
                    "tier",
                    "flags"
            );

            for (String field : requiredFields) {
                if (!document.containsKey(field)) {
                    throw new IllegalArgumentException(
                            "Stored finding is missing a required field"
                    );
                }
            }

            String itemId = document.getString("itemId");

            if (!Objects.equals(document.get("_id"), itemId)) {
                throw new IllegalArgumentException(
                        "Stored finding identifiers do not match"
                );
            }

            return new CodedFinding(
                    itemId,
                    document.getString("sourceRef"),
                    document.getString("speakerCode"),
                    document.getString("theme"),
                    document.getString("quote"),
                    document.getDouble("confidence"),
                    document.getInteger("tier"),
                    document.getList("flags", String.class)
            );
        } catch (IllegalArgumentException | ClassCastException exception) {
            // Do not expose stored quotations or other content in errors.
            throw new DataIntegrityViolationException(
                    "Stored finding does not match the finding contract"
            );
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }

    public static final class FindingConflictException
            extends RuntimeException {

        public FindingConflictException() {
            super("A different finding already uses this itemId");
        }
    }
}