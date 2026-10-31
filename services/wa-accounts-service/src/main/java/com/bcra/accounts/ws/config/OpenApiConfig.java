package com.bcra.accounts.ws.config;

import java.util.List;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bcra.accounts.ws.exception.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Metadatos de H4 y ejemplos reutilizables del contrato real de cuentas. */
@Configuration
public class OpenApiConfig {

    /** Define la API sin exigir una sesión para consultar Swagger UI o su especificación. */
    @Bean
    public OpenAPI accountsOpenApi(@Value("${server.port:8084}") int port, ObjectMapper objectMapper)
            throws JsonProcessingException {
        Components components = new Components();
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);
        components.addExamples("Cuentas", new Example().value(objectMapper.readTree("""
                [
                  {
                    "numeroCuenta": "001",
                    "alias": "ahorros",
                    "tipo": "CA",
                    "moneda": "ARS"
                  }
                ]
                """.strip())));
        components.addExamples("Saldo", new Example().value(objectMapper.readTree("""
                {
                  "numeroCuenta": "001",
                  "alias": "ahorros",
                  "saldoDisponible": 15000,
                  "saldoContable": 15000,
                  "moneda": "ARS"
                }
                """.strip())));
        components.addExamples("Movimientos", new Example().value(objectMapper.readTree("""
                {
                  "movimientos": [
                    {
                      "fecha": "2026-10-27T12:00:00Z",
                      "tipo": "DEBITO",
                      "monto": 100,
                      "moneda": "ARS",
                      "descripcion": "Transferencia",
                      "referencia": "ref-001",
                      "saldoResultante": 14900
                    }
                  ],
                  "tieneMas": false,
                  "cursor": null
                }
                """.strip())));
        components.addExamples("Terceros", new Example().value(objectMapper.readTree("""
                [
                  {
                    "id": "11111111-1111-4111-8111-111111111111",
                    "alias": "Ana",
                    "numeroCuentaDestino": "003",
                    "bancoDestino": "Banco local"
                  }
                ]
                """.strip())));
        components.addExamples("Preparada", new Example().value(objectMapper.readTree("""
                {
                  "transferenciaId": "22222222-2222-4222-8222-222222222222",
                  "estado": "PREPARADA",
                  "resumen": "Transferir 100.00 ARS desde ahorros a gastos",
                  "requiereDobleConfirmacion": false,
                  "expiraEn": "2026-10-27T12:05:00Z"
                }
                """.strip())));
        components.addExamples("Ejecutada", new Example().value(objectMapper.readTree("""
                {
                  "transferenciaId": "22222222-2222-4222-8222-222222222222",
                  "estado": "EJECUTADA",
                  "fechaEjecucion": "2026-10-27T12:01:00Z",
                  "referencia": "ref-001",
                  "comprobante": {
                    "fecha": "2026-10-27T12:01:00Z",
                    "origen": "001",
                    "destino": "002",
                    "monto": 100,
                    "moneda": "ARS",
                    "referencia": "ref-001"
                  }
                }
                """.strip())));
        components.addExamples("Confirmada", new Example().value(objectMapper.readTree("""
                {
                  "transferenciaId": "22222222-2222-4222-8222-222222222222",
                  "estado": "CONFIRMADA",
                  "fechaEjecucion": null,
                  "referencia": null,
                  "comprobante": null
                }
                """.strip())));
        components.addExamples("ValidationError", new Example().value(objectMapper.readTree("""
                {
                  "code": "VALIDATION_ERROR",
                  "message": "La solicitud tiene datos inválidos",
                  "details": [
                    {
                      "field": "cuentaOrigen",
                      "message": "no debe estar vacío"
                    }
                  ],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("BadRequest", new Example().value(objectMapper.readTree("""
                {
                  "code": "BAD_REQUEST",
                  "message": "La solicitud no es válida",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("Unauthorized", new Example().value(objectMapper.readTree("""
                {
                  "code": "UNAUTHORIZED",
                  "message": "Credencial no válida",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("Forbidden", new Example().value(objectMapper.readTree("""
                {
                  "code": "FORBIDDEN",
                  "message": "Acceso no permitido",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("NotFound", new Example().value(objectMapper.readTree("""
                {
                  "code": "NOT_FOUND",
                  "message": "La cuenta no existe",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("Conflict", new Example().value(objectMapper.readTree("""
                {
                  "code": "CONFLICT",
                  "message": "Saldo disponible insuficiente",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("InternalError", new Example().value(objectMapper.readTree("""
                {
                  "code": "INTERNAL_ERROR",
                  "message": "Error interno",
                  "details": [],
                  "timestamp": "2026-10-27T12:00:00Z"
                }
                """.strip())));
        components.addExamples("NotFoundMovimiento", new Example().value(objectMapper.readTree("""
                {"code":"NOT_FOUND","message":"Cuenta no encontrada","details":[],
                 "timestamp":"2026-10-27T12:00:00Z"}
                """.strip())));
        components.addExamples("NotFoundTransferencia", new Example().value(objectMapper.readTree("""
                {"code":"NOT_FOUND","message":"Transferencia no encontrada","details":[],
                 "timestamp":"2026-10-27T12:00:00Z"}
                """.strip())));
        return new OpenAPI()
                .info(new Info().title("wa-accounts-service").version("0.1.0-SNAPSHOT")
                        .description("Servicio de Cuentas (H4): cuentas, saldos, movimientos, terceros agendados "
                                + "y transferencias con preparación y confirmación explícita. "
                                + "Cada operación valida X-Credencial-Sesion con H2. "
                                + "Los ejemplos usan datos ficticios; reemplazar cuentas e identificadores "
                                + "por datos del titular y la credencial por una sesión vigente."))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(components);
    }
}

