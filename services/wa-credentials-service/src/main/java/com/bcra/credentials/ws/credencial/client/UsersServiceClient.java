package com.bcra.credentials.ws.credencial.client;

/** Consulta del estado bancario del cliente en H1. */
public interface UsersServiceClient {

    /**
     * Consulta el estado sin autorizar operaciones por defecto ante fallas de H1.
     * @return estado informado por H1
     * @throws ClienteNoEncontradoException si H1 responde 404
     * @throws UsersServiceUnavailableException si la consulta no puede completarse
     */
    ClienteEstadoResponse consultarEstado(String tipo, String numero);
}
