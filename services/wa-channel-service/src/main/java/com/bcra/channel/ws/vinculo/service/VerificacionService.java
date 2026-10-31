package com.bcra.channel.ws.vinculo.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import com.bcra.channel.ws.vinculo.client.ClienteEstadoResponse.EstadoCliente;
import com.bcra.channel.ws.vinculo.client.UsersServiceClient;
import com.bcra.channel.ws.vinculo.dto.VerificacionResultadoResponse;
import com.bcra.channel.ws.vinculo.dto.VerificarIdentidadRequest;
import com.bcra.channel.ws.vinculo.entity.AuditAccion;
import com.bcra.channel.ws.vinculo.entity.AuditResultado;
import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;
import com.bcra.channel.ws.vinculo.exception.ClienteNoHabilitadoException;
import com.bcra.channel.ws.vinculo.exception.ClienteNoEncontradoException;
import com.bcra.channel.ws.vinculo.exception.NumeroYaVinculadoException;
import com.bcra.channel.ws.vinculo.exception.VerificacionPausadaException;
import com.bcra.channel.ws.vinculo.exception.VinculoNoEncontradoException;
import com.bcra.channel.ws.vinculo.mapper.VerificacionMapper;
import com.bcra.channel.ws.vinculo.repository.IntentoVerificacionRepository;
import com.bcra.channel.ws.vinculo.repository.VinculoRepository;

import lombok.RequiredArgsConstructor;

/** Orquesta identidad, límites de intentos y activación bajo bloqueo transaccional por número. */
@Service
@RequiredArgsConstructor
public class VerificacionService {

    private final VinculoRepository vinculoRepository;
    private final IntentoVerificacionRepository intentoRepository;
    private final UsersServiceClient usersClient;
    private final VerificadorIdentidad verificador;
    private final VinculoAuditService auditoria;
    private final VerificacionMapper mapper;

    @Value("${vinculo.verificacion.max-intentos:3}")
    private int maxIntentos = 3;

    @Value("${vinculo.verificacion.ventana-minutos:15}")
    private int ventanaMinutos = 15;

    /**
     * Verifica únicamente procesos con términos aceptados y crea un intento sin conservar las pruebas.
     * @param numero número E.164
     * @param request identidad y pruebas efímeras
     * @return resultado consumible por el orquestador, sin emitir credenciales
     */
    @Transactional
    public VerificacionResultadoResponse verificar(String numero, VerificarIdentidadRequest request) {
        if (numero == null || !numero.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("El número de WhatsApp debe estar en formato E.164");
        }
        if (maxIntentos < 1 || ventanaMinutos < 1) {
            throw new IllegalStateException("La política de verificación debe tener límites positivos");
        }
        List<Vinculo> vinculos = vinculoRepository.bloquearPorNumero(numero);
        Vinculo pendiente = vinculos.stream()
                .filter(v -> v.getEstado() == VinculoEstado.PENDIENTE_VERIFICACION && v.isTerminosAceptados())
                .filter(v -> v.getDocumentoTipo() == null || mismaIdentidad(v, request))
                .findFirst().orElseThrow(VinculoNoEncontradoException::new);
        try {
            if (usersClient.consultarEstado(request.documentoTipo(), request.documentoNumero()).estado()
                    != EstadoCliente.ACTIVE) {
                auditar(AuditAccion.VERIFICACION_FALLIDA, false, numero, request);
                throw new ClienteNoHabilitadoException();
            }
        } catch (HttpClientErrorException.NotFound exception) {
            auditar(AuditAccion.VERIFICACION_FALLIDA, false, numero, request);
            throw new ClienteNoEncontradoException();
        }
        if (vinculos.stream().anyMatch(v -> v.getEstado() == VinculoEstado.ACTIVO)) {
            auditar(AuditAccion.VINCULACION, false, numero, request);
            throw new NumeroYaVinculadoException();
        }
        Instant ahora = Instant.now();
        Instant inicio = ahora.minus(ventanaMinutos, ChronoUnit.MINUTES);
        long fallidos = vinculos.stream()
                .filter(v -> v.getEstado() == VinculoEstado.PENDIENTE_VERIFICACION)
                .filter(v -> v == pendiente || mismaIdentidad(v, request))
                .mapToLong(v -> intentoRepository.countByVinculoIdAndResultadoAndFechaAfter(
                        v.getId(), ResultadoIntento.FALLIDO, inicio)).sum();
        if (fallidos >= maxIntentos) {
            auditar(AuditAccion.VERIFICACION_FALLIDA, false, numero, request);
            throw new VerificacionPausadaException();
        }
        boolean exitosa = verificador.esIdentidadValida(
                request.documentoTipo(), request.documentoNumero(), request.datosVerificacion());
        pendiente.setDocumentoTipo(request.documentoTipo());
        pendiente.setDocumentoNumero(request.documentoNumero());
        if (exitosa) {
            pendiente.setEstado(VinculoEstado.ACTIVO);
            pendiente.setFechaActivacion(ahora);
            pendiente.setFechaUltimoCambioEstado(ahora);
            pendiente.setMotivoBloqueo(null);
        }
        try {
            vinculoRepository.saveAndFlush(pendiente);
        } catch (DataIntegrityViolationException exception) {
            auditar(AuditAccion.VINCULACION, false, numero, request);
            throw new NumeroYaVinculadoException();
        }
        IntentoVerificacion intento = new IntentoVerificacion();
        intento.setVinculoId(pendiente.getId());
        intento.setFecha(ahora);
        intento.setResultado(exitosa ? ResultadoIntento.EXITOSO : ResultadoIntento.FALLIDO);
        intentoRepository.saveAndFlush(intento);
        auditar(exitosa ? AuditAccion.VERIFICACION_EXITOSA : AuditAccion.VERIFICACION_FALLIDA,
                exitosa, numero, request);
        if (exitosa) {
            boolean revinculacion = vinculos.stream().anyMatch(v -> mismaIdentidad(v, request)
                    && (v.getEstado() == VinculoEstado.DESVINCULADO || v.getEstado() == VinculoEstado.BLOQUEADO));
            auditar(revinculacion ? AuditAccion.REVINCULACION : AuditAccion.VINCULACION, true, numero, request);
        }
        return mapper.toResponse(pendiente, exitosa,
                exitosa ? null : (int) (maxIntentos - fallidos - 1), exitosa ? ahora : null);
    }

    private boolean mismaIdentidad(Vinculo vinculo, VerificarIdentidadRequest request) {
        return Objects.equals(vinculo.getDocumentoTipo(), request.documentoTipo())
                && Objects.equals(vinculo.getDocumentoNumero(), request.documentoNumero());
    }

    private void auditar(AuditAccion accion, boolean exitosa, String numero, VerificarIdentidadRequest request) {
        auditoria.registrar(accion, exitosa ? AuditResultado.EXITOSO : AuditResultado.RECHAZADO,
                numero, request.documentoTipo(), request.documentoNumero(), null);
    }
}
