package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstalledMockitoMockFailureDemoTest {

    @Inject
    GreetingService greetingService;

    @Mock
    GreetingSource greetingSource;

    AutoCloseable mocks;

    @BeforeAll
    void installMockitoMockOnce() {
        mocks = MockitoAnnotations.openMocks(this);
        QuarkusMock.installMockForType(greetingSource, GreetingSource.class);
    }

    @AfterAll
    void closeMockitoMocks() throws Exception {
        mocks.close();
    }

    @Test
    @Order(1)
    void leak_setup_configures_stubbing() {
        when(greetingSource.greeting()).thenReturn("leaked greeting");

        String observed = greetingService.greet();

        MockDiagnostics.print("failure demo setup", greetingSource, observed);
        assertEquals("leaked greeting", observed);
    }

    @Test
    @Order(2)
    void victim_expects_unconfigured_mock() {
        String observed = greetingService.greet();

        MockDiagnostics.print("failure demo victim", greetingSource, observed);
        assertNull(observed);
    }
}
