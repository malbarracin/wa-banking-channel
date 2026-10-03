package com.bank.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del Servicio de Usuarios (H1).
 *
 * <p>Sin base de datos de producción, arrancar con el perfil {@code test}
 * (H2 en memoria, classpath de test):
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=test}.
 */
@SpringBootApplication
public class UsersServiceApplication {

    /**
     * Arranca la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(UsersServiceApplication.class, args);
    }
}
