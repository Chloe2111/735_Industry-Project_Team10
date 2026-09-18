package com.vcnity.backend.commission;

import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CommissionService {

    private static final Set<String> ALLOWED_TIERS = Set.of("Tier 1", "Tier 2", "Tier 3");

    private final CommissionRepository repository;

    public CommissionService(CommissionRepository repository) {
        this.repository = repository;
    }

    public Commission create(CreateCommissionRequest request) {
        // Null tier means "pending AI classification" -- only checked
        // against the allowed set when the caller actually supplies one.
        if (request.getTier() != null && !ALLOWED_TIERS.contains(request.getTier())) {
            throw new InvalidCommissionFieldException(
                    "tier", "Tier must be one of: Tier 1, Tier 2, Tier 3.");
        }

        Commission commission = new Commission(
                request.getTitle(),
                request.getDescription(),
                request.getIncentive(),
                request.getDeadline(),
                request.getTier(),
                request.getGroups(),
                request.getReportFormats());

        return repository.save(commission);
    }
}
