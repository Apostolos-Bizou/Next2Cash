package com.next2me.next2cash.service.migration;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DriveDownloadService}.
 *
 * <p>Strategy: rather than mocking the full Drive SDK (which requires a lot
 * of scaffolding around {@code AbstractGoogleJsonClient}, {@code HttpTransport},
 * etc.), we test the {@link DriveDownloadService#executeWithRetry} method
 * directly by passing in {@link DriveDownloadService.DriveCall} lambdas that
 * throw the exceptions we want to simulate. This keeps tests small, fast, and
 * focused on the logic we actually wrote (retry policy + exception
 * classification) rather than on SDK glue code.
 *
 * <p>The {@link DriveDownloadService#download(String)} method itself is tested
 * only for its input-validation behavior (null/blank driveId). The real
 * download happy path is covered in the pilot dry-run and the pilot execution,
 * where the Drive SDK is exercised against a sample of real file IDs.
 *
 * @since Session #31 - Documents Migration Phase 2 Pilot
 */
class DriveDownloadServiceTest {

    private DriveDownloadService service;

    @BeforeEach
    void setUp() {
        // We instantiate the service without calling init() - the retry logic
        // under test does not need an initialized Drive client.
        service = new DriveDownloadService("credentials/doesnotmatter.json");
    }

    // ========================================================================
    // Input validation
    // ========================================================================

    @Test
    @DisplayName("download(null) throws IllegalArgumentException")
    void downloadRejectsNullDriveId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.download(null));
        assertTrue(ex.getMessage().toLowerCase().contains("null"),
                "Error message should mention 'null': " + ex.getMessage());
    }

    @Test
    @DisplayName("download(blank) throws IllegalArgumentException")
    void downloadRejectsBlankDriveId() {
        assertThrows(IllegalArgumentException.class, () -> service.download(""));
        assertThrows(IllegalArgumentException.class, () -> service.download("   "));
    }

    @Test
    @DisplayName("download() without init throws IllegalStateException")
    void downloadWithoutInitThrows() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.download("some-valid-id-12345"));
        assertTrue(ex.getMessage().toLowerCase().contains("not initialized"),
                "Error message should mention initialization: " + ex.getMessage());
    }

    // ========================================================================
    // Permanent errors (no retry)
    // ========================================================================

    @Test
    @DisplayName("HTTP 404 throws DriveFileNotFoundException immediately (no retry)")
    void http404NotRetried() {
        AtomicInteger callCount = new AtomicInteger(0);

        DriveFileNotFoundException ex = assertThrows(
                DriveFileNotFoundException.class,
                () -> service.executeWithRetry("abc123", "test op", () -> {
                    callCount.incrementAndGet();
                    throw build404();
                }));

        assertEquals(1, callCount.get(), "404 should not be retried");
        assertEquals("abc123", ex.getDriveId());
        assertTrue(ex.getMessage().contains("abc123"));
    }

    @Test
    @DisplayName("HTTP 403 throws DrivePermissionDeniedException immediately (no retry)")
    void http403NotRetried() {
        AtomicInteger callCount = new AtomicInteger(0);

        DrivePermissionDeniedException ex = assertThrows(
                DrivePermissionDeniedException.class,
                () -> service.executeWithRetry("abc123", "test op", () -> {
                    callCount.incrementAndGet();
                    throw build403();
                }));

        assertEquals(1, callCount.get(), "403 should not be retried");
        assertEquals("abc123", ex.getDriveId());
    }

    @Test
    @DisplayName("HTTP 401 (invalid credentials) throws DriveDownloadException without retry")
    void http401NotRetried() {
        AtomicInteger callCount = new AtomicInteger(0);

        DriveDownloadException ex = assertThrows(
                DriveDownloadException.class,
                () -> service.executeWithRetry("abc123", "test op", () -> {
                    callCount.incrementAndGet();
                    throw buildGoogleJsonError(401, "Unauthorized");
                }));

        assertEquals(1, callCount.get(), "401 should not be retried (non-retryable)");
        assertEquals("abc123", ex.getDriveId());
        assertTrue(ex.getMessage().contains("401"));
    }

    // ========================================================================
    // Transient errors (retry with backoff)
    // ========================================================================

    @Test
    @DisplayName("HTTP 429 retried then succeeds on second attempt")
    void http429RetriedAndSucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = service.executeWithRetry("abc123", "test op", () -> {
            int n = callCount.incrementAndGet();
            if (n == 1) {
                throw buildGoogleJsonError(429, "Rate limited");
            }
            return "success-after-retry";
        });

        assertEquals("success-after-retry", result);
        assertEquals(2, callCount.get(), "Should have been called twice (1 fail + 1 success)");
    }

    @Test
    @DisplayName("HTTP 503 retried three times, all fail, throws DriveDownloadException")
    void http503ExhaustsRetries() {
        AtomicInteger callCount = new AtomicInteger(0);

        DriveDownloadException ex = assertThrows(
                DriveDownloadException.class,
                () -> service.executeWithRetry("abc123", "test op", () -> {
                    callCount.incrementAndGet();
                    throw buildGoogleJsonError(503, "Service unavailable");
                }));

        assertEquals(3, callCount.get(), "Should have tried MAX_RETRIES=3 times");
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    @DisplayName("IOException retried and eventually succeeds")
    void ioExceptionRetriedAndSucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = service.executeWithRetry("abc123", "test op", () -> {
            int n = callCount.incrementAndGet();
            if (n < 3) {
                throw new IOException("Connection reset");
            }
            return "success-on-third-try";
        });

        assertEquals("success-on-third-try", result);
        assertEquals(3, callCount.get());
    }

    @Test
    @DisplayName("IOException exhausts retries, throws DriveDownloadException with IOException cause")
    void ioExceptionExhaustsRetries() {
        AtomicInteger callCount = new AtomicInteger(0);

        DriveDownloadException ex = assertThrows(
                DriveDownloadException.class,
                () -> service.executeWithRetry("abc123", "test op", () -> {
                    callCount.incrementAndGet();
                    throw new IOException("Persistent network failure");
                }));

        assertEquals(3, callCount.get());
        assertTrue(ex.getCause() instanceof IOException);
        assertEquals("Persistent network failure", ex.getCause().getMessage());
    }

    // ========================================================================
    // Successful first-try calls
    // ========================================================================

    @Test
    @DisplayName("Happy path: first call succeeds, no retries")
    void firstCallSucceedsNoRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = service.executeWithRetry("abc123", "test op", () -> {
            callCount.incrementAndGet();
            return "hello";
        });

        assertEquals("hello", result);
        assertEquals(1, callCount.get());
    }

    @Test
    @DisplayName("executeWithRetry preserves return value of the call")
    void returnValuePreserved() {
        byte[] expected = new byte[]{1, 2, 3, 4, 5};

        byte[] actual = service.executeWithRetry("abc", "test", () -> expected);

        assertArrayEquals(expected, actual);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private GoogleJsonResponseException build404() {
        return buildGoogleJsonError(404, "File not found: abc123");
    }

    private GoogleJsonResponseException build403() {
        return buildGoogleJsonError(403, "The user does not have sufficient permissions for file abc123");
    }

    /**
     * Builds a GoogleJsonResponseException with the given status code and message.
     * The Drive SDK does not expose a simple constructor, so we use the
     * HttpResponseException.Builder pattern and then wrap.
     */
    private GoogleJsonResponseException buildGoogleJsonError(int statusCode, String message) {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
                statusCode,
                statusCodeReason(statusCode),
                new HttpHeaders());
        builder.setMessage(message);

        GoogleJsonError details = new GoogleJsonError();
        details.setCode(statusCode);
        details.setMessage(message);

        return new GoogleJsonResponseException(builder, details);
    }

    private static String statusCodeReason(int code) {
        return switch (code) {
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }
}
