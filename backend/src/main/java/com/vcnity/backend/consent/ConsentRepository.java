package com.vcnity.backend.consent;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConsentRepository extends MongoRepository<Consent, String> {
}
