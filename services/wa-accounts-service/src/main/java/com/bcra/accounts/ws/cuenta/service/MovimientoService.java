package com.bcra.accounts.ws.cuenta.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.client.dto.ValidarCredencialResponse;
import com.bcra.accounts.ws.cuenta.dto.MovimientosPageResponse;
import com.bcra.accounts.ws.cuenta.entity.AuditoriaResultado;
import com.bcra.accounts.ws.cuenta.entity.Cuenta;
import com.bcra.accounts.ws.cuenta.entity.Movimiento;
import com.bcra.accounts.ws.cuenta.entity.TipoMovimiento;
import com.bcra.accounts.ws.cuenta.mapper.MovimientoMapper;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.repository.MovimientoRepository;
import com.bcra.accounts.ws.cuenta.exception.CuentaMovimientosNoEncontradaException;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

/** Consulta movimientos del titular con ventana limitada y cursor por fecha e identificador. */
@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final CredencialesServiceClient credenciales;
    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final MovimientoMapper mapper;
    private final MovimientoAuditoriaService auditoria;

    @Value("${limits.movimientos.ventana-dias:90}")
    private int ventanaDias;

    @Value("${limits.movimientos.tope-por-mensaje:10}")
    private int topePorMensaje;

    /**
     * Consulta fechas inclusivas UTC. Sin fechas se utiliza la ventana máxima hasta ahora.
     * El cursor continúa únicamente dentro de la cuenta y filtros originales.
     */
    @Transactional(readOnly = true)
    public MovimientosPageResponse listar(String credencial, String numero, Instant desde, Instant hasta,
            TipoMovimiento tipo, Integer limit, String cursor) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        ValidarCredencialResponse identidad = credenciales.validar(credencial);
        if (!identidad.vigente() || !identidad.fechaExpiracion().isAfter(Instant.now())) {
            throw new UnauthorizedException("Credencial no válida");
        }
        Cuenta cuenta = cuentas.findByNumeroCuentaAndTitularDocumentoTipoAndTitularDocumentoNumero(
                numero, identidad.documentoTipo(), identidad.documentoNumero()).orElseThrow(() -> {
                    auditoria.registrar(identidad, null, AuditoriaResultado.RECHAZADO);
                    return new CuentaMovimientosNoEncontradaException();
                });
        try {
            MovimientosPageResponse respuesta = consultar(cuenta.getId(), desde, hasta, tipo, limit, cursor);
            auditoria.registrar(identidad, cuenta.getId(), AuditoriaResultado.EXITOSO);
            return respuesta;
        } catch (IllegalArgumentException exception) {
            auditoria.registrar(identidad, cuenta.getId(), AuditoriaResultado.RECHAZADO);
            throw exception;
        }
    }

    private MovimientosPageResponse consultar(UUID cuentaId, Instant desde, Instant hasta,
            TipoMovimiento tipo, Integer limit, String cursor) {
        if (ventanaDias < 1 || topePorMensaje < 1 || topePorMensaje == Integer.MAX_VALUE) {
            throw new IllegalStateException("Configuración de movimientos inválida");
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("El límite debe ser positivo");
        }
        int tamanio = limit == null ? topePorMensaje : Math.min(limit, topePorMensaje);
        CursorMovimiento anterior = cursor == null ? null : decodificar(cursor);
        Instant fin = hasta == null ? (anterior == null ? Instant.now() : anterior.hasta()) : hasta;
        Instant inicio = desde == null
                ? (anterior == null ? fin.minus(ventanaDias, ChronoUnit.DAYS) : anterior.desde()) : desde;
        if (inicio.isAfter(fin) || inicio.isBefore(fin.minus(ventanaDias, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("El rango excede la ventana máxima o está invertido");
        }
        if (anterior != null && (!anterior.cuentaId().equals(cuentaId) || !anterior.desde().equals(inicio)
                || !anterior.hasta().equals(fin) || !anterior.tipo().equals(tipo == null ? "" : tipo.name()))) {
            throw new IllegalArgumentException("El cursor no corresponde a la consulta");
        }
        Specification<Movimiento> filtros = (root, query, builder) -> builder.and(
                builder.equal(root.get("cuenta").get("id"), cuentaId),
                builder.between(root.get("fecha"), inicio, fin));
        if (tipo != null) {
            filtros = filtros.and((root, query, builder) -> builder.equal(root.get("tipo"), tipo));
        }
        if (anterior != null) {
            filtros = filtros.and((root, query, builder) -> builder.or(
                    builder.lessThan(root.get("fecha"), anterior.fecha()),
                    builder.and(builder.equal(root.get("fecha"), anterior.fecha()),
                            builder.lessThan(root.<UUID>get("id"), anterior.id()))));
        }
        List<Movimiento> encontrados = movimientos.findAll(filtros,
                PageRequest.of(0, tamanio + 1, Sort.by(Sort.Direction.DESC, "fecha", "id"))).getContent();
        boolean tieneMas = encontrados.size() > tamanio;
        List<Movimiento> pagina = encontrados.subList(0, Math.min(tamanio, encontrados.size()));
        String siguiente = tieneMas ? codificar(cuentaId, inicio, fin, tipo, pagina.getLast()) : null;
        return new MovimientosPageResponse(pagina.stream().map(mapper::toResponse).toList(), tieneMas, siguiente);
    }

    private String codificar(UUID cuentaId, Instant desde, Instant hasta, TipoMovimiento tipo, Movimiento ultimo) {
        String contenido = String.join("|", cuentaId.toString(), desde.toString(), hasta.toString(),
                tipo == null ? "" : tipo.name(), ultimo.getFecha().toString(), ultimo.getId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(contenido.getBytes(StandardCharsets.UTF_8));
    }

    private CursorMovimiento decodificar(String cursor) {
        try {
            String contenido = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = contenido.split("\\|", -1);
            if (partes.length != 6) {
                throw new IllegalArgumentException("Cursor inválido");
            }
            return new CursorMovimiento(UUID.fromString(partes[0]), Instant.parse(partes[1]),
                    Instant.parse(partes[2]), partes[3], Instant.parse(partes[4]), UUID.fromString(partes[5]));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Cursor inválido", exception);
        }
    }

    private record CursorMovimiento(UUID cuentaId, Instant desde, Instant hasta, String tipo, Instant fecha, UUID id) {
    }
}
