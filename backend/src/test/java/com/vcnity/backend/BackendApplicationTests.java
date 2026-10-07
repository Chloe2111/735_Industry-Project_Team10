package com.vcnity.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// intake.store=memory: the intake service restores its review queue when the app starts, which reads
// the submission store. With the in-memory store this test still needs no database and stays fast.
// The MongoDB stores are exercised by MongoIntakeStoresIntegrationTest (opt-in, needs a local MongoDB).
@SpringBootTest(properties = "intake.store=memory")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Spring Data MongoDB's client connects lazily, so this passes
        // even without a reachable database -- it only proves the app
        // wires together and config loads correctly.
    }
}
