package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
class PlainMockitoMockControlTest {

    @Mock
    GreetingSource plainMock;

    @Inject
    GreetingService greetingService;

    @Test
    void plain_mock_is_not_installed_into_cdi() {
        when(plainMock.greeting()).thenReturn("plain Mockito greeting");

        assertEquals("plain Mockito greeting", plainMock.greeting());
        assertEquals("real greeting", greetingService.greet());
    }
}
