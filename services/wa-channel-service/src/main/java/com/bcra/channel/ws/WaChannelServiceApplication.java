package com.bcra.channel.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del servicio de canal WhatsApp.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WaChannelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaChannelServiceApplication.class, args);
    }
}
