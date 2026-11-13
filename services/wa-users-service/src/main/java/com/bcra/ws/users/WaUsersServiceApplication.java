package com.bcra.ws.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Base package: com.bcra.ws.users (from project.yaml java.base_package).
 *
 * Bootstrap de la aplicación wa-users-service.
 *
 * @since 2026-10-19
 */
@SpringBootApplication
public class WaUsersServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaUsersServiceApplication.class, args);
    }
}
