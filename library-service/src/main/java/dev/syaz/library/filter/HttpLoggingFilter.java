package dev.syaz.library.filter;

import dev.syaz.library.configuration.HttpLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class HttpLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    private static final String REQUEST_ID_MDC_KEY = "request_id";
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

    private final HttpLoggingProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // A zero cache limit retains all bytes consumed by the application.
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, 0);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedNanos = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                logExchange(cachedRequest, cachedResponse, startedNanos, failure);
            } catch (RuntimeException loggingError) {
                log.atWarn()
                        .addKeyValue("event", "http.logging_failed")
                        .setCause(loggingError)
                        .log("Could not record HTTP exchange");
            } finally {
                copyResponseAndRestoreContext(cachedResponse, previousContext);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String suppliedId = request.getHeader(REQUEST_ID_HEADER);
        if (suppliedId != null && REQUEST_ID_PATTERN.matcher(suppliedId).matches()) {
            return suppliedId;
        }
        return UUID.randomUUID().toString();
    }

    private void logExchange(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
                             long startedNanos, Throwable failure) {
        double durationMs = (System.nanoTime() - startedNanos) / 1_000_000.0;
        int status = failure != null ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
        LoggingEventBuilder event = eventForStatus(status);
        event.addKeyValue("event", "http.exchange")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("query_string", request.getQueryString())
                .addKeyValue("request_content_type", request.getContentType())
                .addKeyValue("request_body", new String(request.getContentAsByteArray(), StandardCharsets.UTF_8))
                .addKeyValue("status", status)
                .addKeyValue("response_content_type", response.getContentType())
                .addKeyValue("response_body", new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .addKeyValue("duration_ms", durationMs);
        if (failure != null) {
            event.setCause(failure);
        }
        event.log("HTTP exchange completed");
    }

    private LoggingEventBuilder eventForStatus(int status) {
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            return log.atError();
        }
        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            return log.atWarn();
        }
        return log.atInfo();
    }

    private void copyResponseAndRestoreContext(ContentCachingResponseWrapper response, Map<String, String> previousContext)
            throws IOException {
        try {
            response.copyBodyToResponse();
        } finally {
            if (previousContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    }
}
