package com.next2me.next2cash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal smoke test to verify the test infrastructure is wired correctly.
 *
 * This test does NOT hit the database, controllers, or any Spring-managed component.
 * It just verifies that:
 *   - JUnit 5 is on the classpath
 *   - AssertJ is on the classpath
 *   - The test runner can discover and execute tests
 *
 * If this test fails, there's a fundamental problem with the test setup.
 * If this test passes, we can move on to real integration tests.
 */
class SmokeTest {

    @Test
    void smokeTest_passes() {
        assertThat(1 + 1).isEqualTo(2);
    }

    @Test
    void smokeTest_stringOperations() {
        String hello = "hello";
        assertThat(hello).isNotNull();
        assertThat(hello.length()).isEqualTo(5);
        assertThat(hello.toUpperCase()).isEqualTo("HELLO");
    }
}