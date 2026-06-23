package com.wa.banking.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del servicio de canal WhatsApp.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@SpringBootApplication
public class WaChannelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaChannelServiceApplication.class, args);
    }
}
