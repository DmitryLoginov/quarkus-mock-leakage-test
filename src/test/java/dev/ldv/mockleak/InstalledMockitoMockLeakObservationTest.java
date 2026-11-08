package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockingDetails;
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
class InstalledMockitoMockLeakObservationTest {

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
    void first_method_configures_stubbing() {
        when(greetingSource.greeting()).thenReturn("leaked greeting");

        String observed = greetingService.greet();

        MockDiagnostics.print("installed @Mock first method", greetingSource, observed);
        assertEquals("leaked greeting", observed);
        assertEquals(1, mockingDetails(greetingSource).getStubbings().size());
    }

    @Test
    @Order(2)
    void second_method_observes_surviving_stubbing() {
        String observed = greetingService.greet();

        MockDiagnostics.print("installed @Mock second method", greetingSource, observed);
        assertEquals("leaked greeting", observed);
        assertEquals(1, mockingDetails(greetingSource).getStubbings().size());
    }
}
