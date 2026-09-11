package com.vcnity.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Spring Data MongoDB's client connects lazily, so this passes
        // even without a reachable database -- it only proves the app
        // wires together and config loads correctly.
    }
}
