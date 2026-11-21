package com.bcra.users.ws.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

import com.bcra.users.ws.config.CorrelationIdFilter;
import com.bcra.users.ws.config.JacksonConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class, JacksonConfig.class})
class GlobalExceptionHandlerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void validationUsesCommonContract() throws Exception {
        MvcResult result = mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("name"))
                .andReturn();
        assertContract(result, "VALIDATION_ERROR");
    }

    @Test
    void badRequestsUseCommonContract() throws Exception {
        for (String path : new String[]{"/test/parameter", "/test/parameter?number=abc", "/test/illegal"}) {
            assertContract(mvc.perform(get(path)).andExpect(status().isBadRequest()).andReturn(), "BAD_REQUEST");
        }
        assertContract(mvc.perform(post("/test").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andReturn(), "BAD_REQUEST");
    }

    @Test
    void domainErrorsUseCommonContract() throws Exception {
        assertContract(mvc.perform(get("/test/missing")).andExpect(status().isNotFound()).andReturn(), "NOT_FOUND");
        for (String path : new String[]{"/test/duplicate", "/test/status"}) {
            assertContract(mvc.perform(get(path)).andExpect(status().isConflict()).andReturn(), "CONFLICT");
        }
    }

    @Test
    void springClientErrorsUseCommonContract() throws Exception {
        assertContract(mvc.perform(get("/test/header")).andExpect(status().isBadRequest()).andReturn(), "BAD_REQUEST");
        assertContract(mvc.perform(delete("/test")).andExpect(status().isMethodNotAllowed()).andReturn(), "BAD_REQUEST");
        assertContract(mvc.perform(post("/test").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType()).andReturn(), "BAD_REQUEST");
        assertContract(mvc.perform(get("/nonexistent")).andExpect(status().isNotFound()).andReturn(), "NOT_FOUND");
    }

    @Test
    void correlationFilterReplacesInvalidIds() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        for (String invalidId : new String[] {"bad value", "x".repeat(65)}) {
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            request.addHeader(CorrelationIdFilter.HEADER, invalidId);
            filter.doFilter(request, response, (req, res) -> { });
            assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
        }
    }

    @Test
    void internalErrorsDoNotExposeDetails() throws Exception {
        MvcResult result = mvc.perform(get("/test/internal")).andExpect(status().isInternalServerError()).andReturn();
        assertContract(result, "INTERNAL_ERROR");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private failure", "Exception", "at ");
    }

    @Test
    void correlationFilterGeneratesAndPreservesIdAndCleansMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("correlationId")).isNotBlank());
        assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
        assertThat(MDC.get("correlationId")).isNull();
        request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "correlation-123");
        response = new org.springframework.mock.web.MockHttpServletResponse();
        MDC.put("correlationId", "previous");
        try {
            filter.doFilter(request, response, (req, res) -> assertThat(MDC.get("correlationId"))
                    .isEqualTo("correlation-123"));
            assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("correlation-123");
            assertThat(MDC.get("correlationId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }

    private void assertContract(MvcResult result, String code) throws Exception {
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("details").isArray()).isTrue();
        assertThat(body.get("timestamp").asText()).endsWith("Z");
        assertThat(Instant.parse(body.get("timestamp").asText())).isNotNull();
    }

    record Request(@NotBlank String name) { }

    @RestController
    static class TestController {
        @PostMapping(value = "/test", consumes = MediaType.APPLICATION_JSON_VALUE)
        void validate(@Valid @RequestBody Request request) { }
        @GetMapping("/test/header") void header(@RequestHeader("X-Actor-Id") String actorId) { }
        @GetMapping("/test/parameter") void parameter(@RequestParam int number) { }
        @GetMapping("/test/illegal") void illegal() { throw new IllegalArgumentException(); }
        @GetMapping("/test/missing") void missing() { throw new UserNotFoundException(); }
        @GetMapping("/test/duplicate") void duplicate() { throw new DuplicateUserException(); }
        @GetMapping("/test/status") void status() { throw new InvalidStatusTransitionException(); }
        @GetMapping("/test/internal") void internal() { throw new IllegalStateException("private failure"); }
    }
}
