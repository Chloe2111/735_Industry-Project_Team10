package com.vcnity.backend.commission;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommissionRepository extends MongoRepository<Commission, String> {
}
