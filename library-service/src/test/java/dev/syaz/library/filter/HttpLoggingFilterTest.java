package dev.syaz.library.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.syaz.library.configuration.HttpLoggingProperties;
import dev.syaz.library.controller.BorrowerController;
import dev.syaz.library.dto.request.RegisterBorrowerRequest;
import dev.syaz.library.dto.response.BorrowerResponse;
import dev.syaz.library.exception.ApiExceptionHandler;
import dev.syaz.library.service.BorrowerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpLoggingFilterTest {
    private static final Long BORROWER_ID = 1L;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final BorrowerService service = mock(BorrowerService.class);
    private final SnapshotAppender appender = new SnapshotAppender();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
    private final Logger errorLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MDC.clear();
        appender.start();
        filterLogger.addAppender(appender);
        errorLogger.addAppender(appender);
        when(service.register(any())).thenAnswer(invocation -> {
            RegisterBorrowerRequest request = invocation.getArgument(0);
            return new BorrowerResponse(BORROWER_ID, request.name(), request.email());
        });
        mvc = mvc(new HttpLoggingProperties(true));
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        errorLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void capturesOriginalBodyTextAndHttpDetailsInOneStructuredEntry() throws Exception {
        String original = "{\n  \"email\" : \" READER@EXAMPLE.COM \",\n  \"name\": \"  Réader 📚  \"\n}\n";
        MvcResult result = mvc.perform(post("/api/v1/borrowers?source=local&debug=true").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "request-one").content(original.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated()).andReturn();

        ILoggingEvent event = exchange();
        JsonNode json = encoded(event);
        assertThat(exchanges()).hasSize(1);
        assertThat(json.path("request_body").asString()).isEqualTo(original);
        JsonNode responseBody = mapper.readTree(json.path("response_body").asString());
        assertThat(responseBody.path("name").asString()).isEqualTo("Réader 📚");
        assertThat(responseBody.path("email").asString()).isEqualTo("reader@example.com");
        assertThat(json.path("response_body").asString())
                .isEqualTo(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("request_id").asString()).isEqualTo("request-one");
        assertThat(json.path("method").asString()).isEqualTo("POST");
        assertThat(json.path("path").asString()).isEqualTo("/api/v1/borrowers");
        assertThat(json.path("query_string").asString()).isEqualTo("source=local&debug=true");
        assertThat(json.path("request_content_type").asString()).isEqualTo("application/json");
        assertThat(json.path("response_content_type").asString()).startsWith("application/json");
        assertThat(json.path("status").asInt()).isEqualTo(201);
        assertThat(json.path("duration_ms").isNumber()).isTrue();
        assertThat(result.getResponse().getHeader("X-Request-ID")).isEqualTo("request-one");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void capturesJsonPatchArraysWithoutChangingTheBody() throws Exception {
        String patchBody = "[ { \"op\": \"test\", \"path\": \"/status\", \"value\": \"BORROWED\" } ]";
        MvcResult result = mvc.perform(patch("/api/test/patch").contentType("application/json-patch+json").content(patchBody))
                .andExpect(status().isOk()).andReturn();

        JsonNode json = encoded(exchange());
        assertThat(json.path("request_body").asString()).isEqualTo(patchBody);
        assertThat(json.path("request_content_type").asString()).isEqualTo("application/json-patch+json");
        assertThat(mapper.readTree(json.path("response_body").asString()).isArray()).isTrue();
        assertThat(json.path("response_body").asString()).isEqualTo(result.getResponse().getContentAsString());
    }

    @Test
    void keepsMalformedJsonAndLogsProblemDetails() throws Exception {
        String original = "{\"name\": broken json}";
        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isBadRequest());

        ILoggingEvent event = exchange();
        JsonNode json = encoded(event);
        assertThat(json.path("request_body").asString()).isEqualTo(original);
        assertThat(mapper.readTree(json.path("response_body").asString()).path("code").asString()).isEqualTo("INVALID_REQUEST");
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void leavesTheLoggedRequestBodyEmptyWhenTheApplicationDoesNotReadIt() throws Exception {
        String original = "{ \"unconsumed\": true }";
        mvc.perform(post("/api/test/not-found").contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isNotFound());

        assertThat(encoded(exchange()).path("request_body").asString()).isEmpty();
        assertThat(value(exchange(), "status")).isEqualTo(404);
    }

    @Test
    void logsValidationFailuresAsJson() throws Exception {
        mvc.perform(post("/api/v1/borrowers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());

        JsonNode json = encoded(exchange());
        JsonNode responseBody = mapper.readTree(json.path("response_body").asString());
        assertThat(responseBody.path("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(responseBody.path("errors").isArray()).isTrue();
    }

    @Test
    void correlatesTaggedExceptionLogsWithTheHttpExchange() throws Exception {
        mvc.perform(get("/api/test/failure").header("X-Request-ID", "failure-one"))
                .andExpect(status().isInternalServerError());

        ILoggingEvent failure = appender.list.stream().filter(event -> "application.error".equals(value(event, "event")))
                .findFirst().orElseThrow();
        assertThat(failure.getMDCPropertyMap().get("request_id")).isEqualTo("failure-one");
        assertThat(value(failure, "error_code")).isEqualTo("INTERNAL_ERROR");
        assertThat(exchange().getLevel()).isEqualTo(Level.ERROR);
        assertThat(mapper.readTree(encoded(exchange()).path("response_body").asString()).path("status").asInt()).isEqualTo(500);
    }

    @Test
    void restoresMdcAndKeepsConsecutiveRequestsSeparate() throws Exception {
        MDC.put("request_id", "outer");
        MDC.put("trace_id", "existing-trace");
        mvc.perform(get("/api/test/empty").header("X-Request-ID", "first" )).andExpect(status().isNoContent());
        assertThat(MDC.get("request_id")).isEqualTo("outer");
        assertThat(MDC.get("trace_id")).isEqualTo("existing-trace");
        mvc.perform(get("/api/test/empty").header("X-Request-ID", "second")).andExpect(status().isNoContent());

        List<ILoggingEvent> events = exchanges();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getMDCPropertyMap().get("request_id")).isEqualTo("first");
        assertThat(events.get(1).getMDCPropertyMap().get("request_id")).isEqualTo("second");
        assertThat(MDC.get("request_id")).isEqualTo("outer");
    }

    @Test
    void replacesAnInvalidRequestIdWithAUuid() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/empty").header("X-Request-ID", "x".repeat(65)))
                .andExpect(status().isNoContent()).andReturn();

        String generated = result.getResponse().getHeader("X-Request-ID");
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(exchange().getMDCPropertyMap().get("request_id")).isEqualTo(generated);
    }

    @Test
    void omitsHeadersFromTheLogAndForwardsThemUnchanged() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/cookies").header("Authorization", "Bearer private-token")
                        .header("Cookie", "session=private-cookie")).andExpect(status().isOk()).andReturn();

        JsonNode json = encoded(exchange());
        assertThat(json.toString()).doesNotContain("private-token", "private-cookie", "private-response-cookie");
        assertThat(json.has("request_headers")).isFalse();
        assertThat(json.has("response_headers")).isFalse();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isEqualTo("session=private-response-cookie");
    }

    @Test
    void disabledLoggingLeavesTheExchangeUntouched() throws Exception {
        mvc = mvc(new HttpLoggingProperties(false));
        MvcResult result = mvc.perform(get("/api/test/empty")).andExpect(status().isNoContent()).andReturn();

        assertThat(exchanges()).isEmpty();
        assertThat(result.getResponse().getHeader("X-Request-ID")).isNull();
    }

    @Test
    void forwardsBinaryResponseBytesUnchanged() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/binary")).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).containsExactly((byte) 0, (byte) 1, (byte) 255);
    }

    @Test
    void capturesUnicodeTextAndForwardsEmptyBodies() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/text")).andExpect(status().isOk()).andReturn();
        assertThat(encoded(exchange()).path("response_body").asString())
                .isEqualTo(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("héllo café");
        mvc.perform(get("/api/test/empty")).andExpect(status().isNoContent());
        assertThat(encoded(exchange()).path("request_body").asString()).isEmpty();
        assertThat(encoded(exchange()).path("response_body").asString()).isEmpty();
    }

    @Test
    void propagatesUnhandledFailuresAndAlwaysCleansUpMdc() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test/unhandled");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("unhandled");

        assertThatThrownBy(() -> new HttpLoggingFilter(new HttpLoggingProperties(true))
                .doFilter(request, response, (req, res) -> { throw failure; })).isSameAs(failure);
        assertThat(value(exchange(), "status")).isEqualTo(500);
        assertThat(exchange().getThrowableProxy()).isNotNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void forwardsTheResponseAndRestoresMdcWhenLogCaptureFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test/logging-failure");
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public String getContentType() { throw new IllegalStateException("metadata unavailable"); }
        };
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        MDC.put("request_id", "outer");

        new HttpLoggingFilter(new HttpLoggingProperties(true)).doFilter(request, response,
                (req, res) -> res.getOutputStream().write(body));

        assertThat(response.getContentAsByteArray()).isEqualTo(body);
        assertThat(MDC.get("request_id")).isEqualTo("outer");
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(value(event, "event")).isEqualTo("http.logging_failed");
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
        });
    }

    @Test
    void cleansUpMdcWhenForwardingTheResponseFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test/disconnected");
        IOException failure = new IOException("client disconnected");
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                return new DelegatingServletOutputStream(new OutputStream() {
                    @Override
                    public void write(int value) throws IOException { throw failure; }
                });
            }
        };

        assertThatThrownBy(() -> new HttpLoggingFilter(new HttpLoggingProperties(true)).doFilter(request, response,
                (req, res) -> res.getOutputStream().write("response".getBytes(StandardCharsets.UTF_8))))
                .isSameAs(failure);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private MockMvc mvc(HttpLoggingProperties properties) {
        return MockMvcBuilders.standaloneSetup(new BorrowerController(service), new FixtureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(),
                        new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter())
                // Match Spring Boot's UTF-8 encoding filter, which runs before HTTP logging.
                .addFilter(new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true))
                .addFilter(new HttpLoggingFilter(properties), "/api/*")
                .build();
    }

    private List<ILoggingEvent> exchanges() {
        return appender.list.stream().filter(event -> "http.exchange".equals(value(event, "event"))).toList();
    }

    private ILoggingEvent exchange() {
        return exchanges().get(exchanges().size() - 1);
    }

    private Object value(ILoggingEvent event, String key) {
        return event.getKeyValuePairs() == null ? null : event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key)).map(pair -> pair.value).findFirst().orElse(null);
    }

    private JsonNode encoded(ILoggingEvent event) {
        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment()
                .withProperty("spring.application.name", "library-service")
                .withProperty("logging.structured.ecs.service.environment", "test"));
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();
        try {
            String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
            assertThat(line.lines().count()).as("one JSON object per physical log line").isEqualTo(1);
            return mapper.readTree(line);
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    private static class SnapshotAppender extends ListAppender<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    }

    @RestController
    static class FixtureController {
        @PatchMapping(value = "/api/test/patch", produces = MediaType.APPLICATION_JSON_VALUE)
        JsonNode patchBody(@RequestBody JsonNode body) { return body; }

        @GetMapping("/api/test/empty")
        ResponseEntity<Void> empty() { return ResponseEntity.noContent().build(); }

        @GetMapping(value = "/api/test/binary", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        byte[] binary() { return new byte[] {0, 1, (byte) 255}; }

        @GetMapping(value = "/api/test/text", produces = "text/plain;charset=UTF-8")
        ResponseEntity<String> text() { return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8)).body("héllo café"); }

        @GetMapping(value = "/api/test/cookies", produces = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<Map<String, Boolean>> cookies() {
            return ResponseEntity.ok().header("Set-Cookie", "session=private-response-cookie").body(Map.of("ok", true));
        }

        @GetMapping("/api/test/failure")
        String failure() { throw new IllegalStateException("test failure"); }
    }
}
