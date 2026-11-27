package com.bcra.users.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WaUsersServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaUsersServiceApplication.class, args);
    }
}
