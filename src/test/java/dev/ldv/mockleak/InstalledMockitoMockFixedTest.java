package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstalledMockitoMockFixedTest {

    @Inject
    GreetingService greetingService;

    @Mock
    GreetingSource greetingSource;

    AutoCloseable mocks;

    @BeforeEach
    void installFreshMockitoMock() {
        mocks = MockitoAnnotations.openMocks(this);
        QuarkusMock.installMockForType(greetingSource, GreetingSource.class);
    }

    @AfterEach
    void closeMockitoMocks() throws Exception {
        mocks.close();
    }

    @Test
    @Order(1)
    void first_method_configures_stubbing() {
        when(greetingSource.greeting()).thenReturn("isolated greeting");

        String observed = greetingService.greet();

        MockDiagnostics.print("fresh installed @Mock first method", greetingSource, observed);
        assertEquals("isolated greeting", observed);
        assertEquals(1, mockingDetails(greetingSource).getStubbings().size());
    }

    @Test
    @Order(2)
    void second_method_has_no_previous_stubbing() {
        String observed = greetingService.greet();

        MockDiagnostics.print("fresh installed @Mock second method", greetingSource, observed);
        assertNull(observed);
        assertEquals(0, mockingDetails(greetingSource).getStubbings().size());
    }
}
