package com.bcra.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SafeExceptionHandlerTest {
    @RestController
    static class FailingController {
        @GetMapping("/failure")
        public void fail() {
            throw new IllegalStateException("private-token whatsapp:+15005550006");
        }
    }

    @Test
    void unexpectedFailureReturnsEmpty500() throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new SafeExceptionHandler())
                .build().perform(get("/failure?trace=true&message=true"))
                .andExpect(status().isInternalServerError()).andExpect(content().string(""));
    }
}
