package com.vcnity.backend.exceptions.config;

import com.vcnity.backend.exceptions.model.ExceptionItem;
import com.vcnity.backend.exceptions.model.FlagType;
import com.vcnity.backend.exceptions.service.ExceptionsQueueService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Lightweight Story 19 MVP seed data so all required AI flags are immediately
 * visible to a reviewer. Real pipeline outcomes can be added through the same
 * queue service/API without changing the UI.
 */
@Component
public class ExceptionsDemoData {
    private final ExceptionsQueueService service;

    public ExceptionsDemoData(ExceptionsQueueService service) {
        this.service = service;
    }

    @PostConstruct
    void seed() {
        if (!service.list().isEmpty()) return;

        service.add(new ExceptionItem(null, FlagType.QUOTE_NOT_FOUND,
                "The community strongly supports the proposed development.",
                "The transcript discusses mixed views about the development, but this exact quoted sentence does not appear in the source.",
                0.88, "community_transcript_04.txt"));

        service.add(new ExceptionItem(null, FlagType.LOW_CONFIDENCE,
                "Residents may prefer additional green space.",
                "The model detected a possible preference, but the available evidence is weak and requires human verification.",
                0.32, "community_transcript_07.txt"));

        service.add(new ExceptionItem(null, FlagType.SOURCE_MISSING,
                "Parking availability was identified as a major concern.",
                "The generated finding has no traceable transcript or source reference attached, so a reviewer must verify or reject it.",
                null, "unresolved_source_item_12"));

        service.add(new ExceptionItem(null, FlagType.CONTESTED,
                "The proposal has broad community agreement.",
                "Evidence contains conflicting statements: some participants support the proposal while others object to its scale and traffic impacts.",
                0.61, "community_transcript_11.txt"));
    }
}
