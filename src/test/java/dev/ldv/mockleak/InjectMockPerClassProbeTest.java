package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InjectMockPerClassProbeTest {

    @Inject
    GreetingService greetingService;

    @InjectMock
    GreetingSource greetingSource;

    @Test
    @Order(1)
    void first_method_configures_stubbing() {
        when(greetingSource.greeting()).thenReturn("per-class greeting");

        String observed = greetingService.greet();

        MockDiagnostics.print("@InjectMock PER_CLASS first method", greetingSource, observed);
        assertEquals("per-class greeting", observed);
    }

    @Test
    @Order(2)
    void second_method_shows_quarkus_clears_previous_stubbing() {
        String observed = greetingService.greet();

        MockDiagnostics.print("@InjectMock PER_CLASS second method", greetingSource, observed);
        assertNull(observed);
    }
}
