package com.wa.banking.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del servicio de acceso y sesión (H2).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@SpringBootApplication
public class WaAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaAuthServiceApplication.class, args);
    }
}
