package dev.ldv.mockleak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BRealBeanAfterInjectMockTest {

    @Inject
    GreetingService greetingService;

    @Test
    void following_test_class_uses_the_real_bean() {
        assertEquals("real greeting", greetingService.greet());
    }
}
