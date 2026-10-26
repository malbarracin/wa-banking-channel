package com.bcra.channel.ws.vinculo.client;

/** Consulta H1; los errores HTTP o de transporte se propagan al caso de uso. */
public interface UsersServiceClient {

    /** Obtiene el estado operativo del cliente identificado por tipo y número de documento. */
    ClienteEstadoResponse consultarEstado(String tipo, String numero);

    /** Obtiene exclusivamente los campos de perfil permitidos para el canal. */
    ClientePerfilResponse consultarPerfil(String tipo, String numero);
}
