package com.bcra.cards.ws.tarjeta.service;

import com.bcra.cards.ws.exception.UnauthorizedException;
import com.bcra.cards.ws.tarjeta.client.CredencialesServiceClient;
import com.bcra.cards.ws.tarjeta.dto.ConsumosPageResponse;
import com.bcra.cards.ws.tarjeta.entity.Consumo;
import com.bcra.cards.ws.tarjeta.exception.TarjetaNoEncontradaException;
import com.bcra.cards.ws.tarjeta.mapper.ConsumoMapper;
import com.bcra.cards.ws.tarjeta.repository.ConsumoRepository;
import com.bcra.cards.ws.tarjeta.repository.TarjetaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autoriza al titular y consulta consumos con ventana temporal fija y cursor por fecha e identificador. */
@Service
public class ConsumoService {

    private static final UUID MAX_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private final CredencialesServiceClient credenciales;
    private final TarjetaRepository tarjetas;
    private final ConsumoRepository consumos;
    private final ConsumoMapper mapper;
    private final int ventanaDias;
    private final int topePorMensaje;

    public ConsumoService(CredencialesServiceClient credenciales, TarjetaRepository tarjetas,
            ConsumoRepository consumos, ConsumoMapper mapper,
            @Value("${cards.consumos.ventana-dias:90}") int ventanaDias,
            @Value("${cards.consumos.tope-por-mensaje:10}") int topePorMensaje) {
        if (ventanaDias < 1 || topePorMensaje < 1 || topePorMensaje == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Configuración de consumos inválida");
        }
        this.credenciales = credenciales;
        this.tarjetas = tarjetas;
        this.consumos = consumos;
        this.mapper = mapper;
        this.ventanaDias = ventanaDias;
        this.topePorMensaje = topePorMensaje;
    }

    /** Valida la sesión antes de consultar productos; los parámetros no pueden exceder los topes del banco. */
    @Transactional(readOnly = true)
    public ConsumosPageResponse listar(UUID tarjetaId, String credencial, Integer dias, Integer limit, String cursor) {
        if (credencial == null || credencial.isBlank()) {
            throw new UnauthorizedException("Credencial no válida");
        }
        var titular = credenciales.validar(credencial);
        tarjetas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(tarjetaId,
                titular.documentoTipo(), titular.documentoNumero()).orElseThrow(TarjetaNoEncontradaException::new);
        int ventana = limitar(dias, ventanaDias);
        int cantidad = limitar(limit, topePorMensaje);
        Instant hasta = Instant.now();
        Instant ultimaFecha = hasta;
        UUID ultimoId = MAX_UUID;
        if (cursor != null) {
            String contenido = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = contenido.split("\\|", -1);
            if (partes.length != 5 || !partes[0].equals(tarjetaId.toString())
                    || !partes[1].equals(Integer.toString(ventana))) {
                throw new IllegalArgumentException("Cursor inválido");
            }
            hasta = parsearFecha(partes[2]);
            ultimaFecha = parsearFecha(partes[3]);
            ultimoId = UUID.fromString(partes[4]);
            if (hasta.isAfter(Instant.now()) || ultimaFecha.isAfter(hasta)
                    || ultimaFecha.isBefore(hasta.minus(ventana, ChronoUnit.DAYS))) {
                throw new IllegalArgumentException("Cursor fuera de ventana");
            }
        }
        Instant desde = hasta.minus(ventana, ChronoUnit.DAYS);
        Instant minimoReciente = Instant.now().minus(ventana, ChronoUnit.DAYS);
        if (desde.isBefore(minimoReciente)) {
            desde = minimoReciente;
        }
        List<Consumo> encontrados = consumos.buscarSiguientes(tarjetaId, desde,
                hasta, ultimaFecha, ultimoId, PageRequest.of(0, cantidad + 1));
        boolean tieneMas = encontrados.size() > cantidad;
        List<Consumo> pagina = encontrados.subList(0, Math.min(cantidad, encontrados.size()));
        String siguiente = null;
        if (tieneMas) {
            Consumo ultimo = pagina.getLast();
            String posicion = tarjetaId + "|" + ventana + "|" + hasta + "|" + ultimo.getFecha() + "|" + ultimo.getId();
            siguiente = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(posicion.getBytes(StandardCharsets.UTF_8));
        }
        return new ConsumosPageResponse(pagina.stream().map(mapper::toResponse).toList(), tieneMas, siguiente);
    }

    private int limitar(Integer valor, int maximo) {
        if (valor != null && valor < 1) {
            throw new IllegalArgumentException("El valor debe ser positivo");
        }
        return valor == null ? maximo : Math.min(valor, maximo);
    }

    private Instant parsearFecha(String valor) {
        try {
            return Instant.parse(valor);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Fecha de cursor inválida", exception);
        }
    }
}
