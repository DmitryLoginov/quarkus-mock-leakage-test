package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GreetingServiceTest {

    @Inject
    GreetingService greetingService;

    @Test
    void real_source_is_used_without_a_mock() {
        assertEquals("real greeting", greetingService.greet());
    }
}
