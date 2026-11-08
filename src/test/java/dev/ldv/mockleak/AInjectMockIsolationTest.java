package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AInjectMockIsolationTest {

    @Inject
    GreetingService greetingService;

    @InjectMock
    GreetingSource greetingSource;

    @Test
    @Order(1)
    void first_method_configures_stubbing() {
        when(greetingSource.greeting()).thenReturn("inject-mock greeting");

        String observed = greetingService.greet();

        MockDiagnostics.print("@InjectMock first method", greetingSource, observed);
        assertEquals("inject-mock greeting", observed);
    }

    @Test
    @Order(2)
    void second_method_has_no_previous_stubbing() {
        String observed = greetingService.greet();

        MockDiagnostics.print("@InjectMock second method", greetingSource, observed);
        assertNull(observed);
    }
}
