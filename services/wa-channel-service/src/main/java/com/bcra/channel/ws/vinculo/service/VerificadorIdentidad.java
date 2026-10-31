package com.bcra.channel.ws.vinculo.service;

import java.util.Map;

/** Puerto para el método de verificación aprobado por Compliance; nunca debe registrar los datos recibidos. */
@FunctionalInterface
public interface VerificadorIdentidad {

    /** Comprueba pruebas de identidad sin conservarlas ni confiar en un resultado declarado por el solicitante. */
    boolean esIdentidadValida(String documentoTipo, String documentoNumero, Map<String, String> datos);
}
