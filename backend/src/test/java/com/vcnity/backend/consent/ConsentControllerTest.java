package com.vcnity.backend.consent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentControllerTest {

    @Mock
    private ConsentRepository consentRepository;

    @InjectMocks
    private ConsentController consentController;

    private ConsentRequest validRequest(int tier, boolean consentGiven) {
        ConsentRequest req = new ConsentRequest();
        req.setGroupName("Youth Group A");
        req.setTier(tier);
        req.setConsentGiven(consentGiven);
        req.setParticipantName(null);
        req.setNotes(null);
        return req;
    }

    @Test
    void rejectsSubmissionWhenConsentNotGiven() {
        ConsentRequest request = validRequest(1, false);
        ResponseEntity<?> response = consentController.submitConsent(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(consentRepository, never()).save(any());
    }

    @Test
    void savesAndReturnsCreatedWhenConsentGiven() {
        ConsentRequest request = validRequest(2, true);
        Consent saved = new Consent(null, "Youth Group A", 2, true, null);
        saved.setId("abc123");
        when(consentRepository.save(any(Consent.class))).thenReturn(saved);

        ResponseEntity<?> response = consentController.submitConsent(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody() instanceof Consent);
        assertEquals("abc123", ((Consent) response.getBody()).getId());
    }

    @Test
    void tier3ConsentIsStoredJustLikeAnyOtherTier() {
        ConsentRequest request = validRequest(3, true);
        Consent saved = new Consent(null, "Elders Circle", 3, true, null);
        when(consentRepository.save(any(Consent.class))).thenReturn(saved);

        ResponseEntity<?> response = consentController.submitConsent(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, ((Consent) response.getBody()).getTier());
    }
}
