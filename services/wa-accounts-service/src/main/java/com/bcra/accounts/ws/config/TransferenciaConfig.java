package com.bcra.accounts.ws.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj UTC sustituible para validar períodos y expiración. */
@Configuration
public class TransferenciaConfig {

    @Bean
    Clock transferenciaClock() {
        return Clock.systemUTC();
    }
}

