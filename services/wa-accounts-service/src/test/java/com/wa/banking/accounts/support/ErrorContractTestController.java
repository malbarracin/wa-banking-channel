package com.wa.banking.accounts.support;

import com.wa.banking.accounts.exception.AccountNotFoundException;
import com.wa.banking.accounts.exception.UnauthorizedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador auxiliar de test para disparar excepciones del contrato de error.
 */
@RestController
@RequestMapping("/api/test/errors")
public class ErrorContractTestController {

    @GetMapping("/bad-request")
    public void badRequest() {
        throw new IllegalArgumentException("Invalid parameter");
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new AccountNotFoundException("acc-missing");
    }

    @GetMapping("/unauthorized")
    public void unauthorized() {
        throw new UnauthorizedException("Invalid session credential");
    }

    @GetMapping("/internal")
    public void internal() {
        throw new IllegalStateException("Unexpected failure");
    }

    @PostMapping("/validation")
    public void validation(@Valid @RequestBody ValidationTestRequest request) {
    }

    public record ValidationTestRequest(
            @NotBlank(message = "name is required")
            String name
    ) {
    }
}
