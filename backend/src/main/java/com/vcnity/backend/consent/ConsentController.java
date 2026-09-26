package com.vcnity.backend.consent;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consent")
@CrossOrigin
public class ConsentController {

    private final ConsentRepository consentRepository;

    public ConsentController(ConsentRepository consentRepository) {
        this.consentRepository = consentRepository;
    }

    @PostMapping
    public ResponseEntity<?> submitConsent(@Valid @RequestBody ConsentRequest request) {
        if (!request.isConsentGiven()) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Consent must be given before this can be recorded."));
        }

        Consent consent = new Consent(
                request.getParticipantName(),
                request.getGroupName(),
                request.getTier(),
                request.isConsentGiven(),
                request.getNotes());

        Consent saved = consentRepository.save(consent);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    static class ErrorResponse {
        public String message;
        public ErrorResponse(String message) { this.message = message; }
    }
}
