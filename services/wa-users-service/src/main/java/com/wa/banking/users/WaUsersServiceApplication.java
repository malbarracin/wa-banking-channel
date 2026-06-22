package com.wa.banking.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del servicio de usuarios del canal bancario WA.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
@SpringBootApplication
public class WaUsersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaUsersServiceApplication.class, args);
    }
}
