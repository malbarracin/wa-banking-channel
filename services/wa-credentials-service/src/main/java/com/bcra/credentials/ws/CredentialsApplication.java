package com.bcra.credentials.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada del servicio de acceso y sesión. */
@SpringBootApplication
public class CredentialsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CredentialsApplication.class, args);
    }
}
