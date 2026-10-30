package com.bcra.cards.ws.tarjeta.repository;

import com.bcra.cards.ws.tarjeta.entity.AuditoriaAccion;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaResultado;
import com.bcra.cards.ws.tarjeta.entity.AuditoriaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.AvisoViaje;
import com.bcra.cards.ws.tarjeta.entity.Bloqueo;
import com.bcra.cards.ws.tarjeta.entity.Consumo;
import com.bcra.cards.ws.tarjeta.entity.EstadoTarjeta;
import com.bcra.cards.ws.tarjeta.entity.MarcaTarjeta;
import com.bcra.cards.ws.tarjeta.entity.Tarjeta;
import com.bcra.cards.ws.tarjeta.entity.TipoBloqueo;
import com.bcra.cards.ws.tarjeta.entity.TipoTarjeta;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TarjetaRepositoriesTest {

    private static final Instant FECHA = Instant.parse("2026-10-30T10:00:00Z");

    @Autowired
    private TarjetaRepository tarjetas;
    @Autowired
    private ConsumoRepository consumos;
    @Autowired
    private BloqueoRepository bloqueos;
    @Autowired
    private AvisoViajeRepository avisos;
    @Autowired
    private AuditoriaTarjetaRepository auditorias;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Persiste tarjetas sin PAN y restringe las consultas al titular indicado")
    void filtraTarjetasPorTitular() {
        Tarjeta propia = guardarTarjeta("10000001");
        guardarTarjeta("10000002");
        entityManager.flush();
        entityManager.clear();

        assertThat(tarjetas.findByTitularDocumentoTipoAndTitularDocumentoNumero("DNI", "10000001"))
                .extracting(Tarjeta::getId).containsExactly(propia.getId());
        Tarjeta recuperada = tarjetas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                propia.getId(), "DNI", "10000001").orElseThrow();
        assertThat(recuperada.getNumeroTarjetaHash()).isEqualTo("hash-sintetico");
        assertThat(recuperada.getUltimosCuatro()).isEqualTo("1234");
        assertThat(recuperada.getMarca()).isEqualTo(MarcaTarjeta.VISA);
        assertThat(recuperada.getTipo()).isEqualTo(TipoTarjeta.CREDITO);
        assertThat(recuperada.getEstado()).isEqualTo(EstadoTarjeta.ACTIVA);
        assertThat(recuperada.isActiva()).isTrue();
        assertThat(recuperada.getCreatedAt()).isNotNull();
        assertThat(recuperada.getUpdatedAt()).isNotNull();
        assertThat(tarjetas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                propia.getId(), "DNI", "10000002")).isEmpty();
        assertThat(tarjetas.findByTitularDocumentoTipoAndTitularDocumentoNumero("PASAPORTE", "10000001"))
                .isEmpty();
        assertThat(entityManager.getEntityManager().createNativeQuery("select estado from tarjetas where id = :id")
                .setParameter("id", propia.getId()).getSingleResult()).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Filtra consumos por tarjeta y ventana, ordenados y paginados")
    void filtraConsumosPorVentana() {
        UUID tarjetaId = guardarTarjeta("10000001").getId();
        Consumo anterior = guardarConsumo(tarjetaId, FECHA.minusSeconds(60));
        Consumo reciente = guardarConsumo(tarjetaId, FECHA);
        guardarConsumo(UUID.randomUUID(), FECHA);
        entityManager.flush();
        entityManager.clear();

        assertThat(consumos.findByTarjetaIdAndFechaAfterOrderByFechaDesc(
                tarjetaId, FECHA.minusSeconds(60), PageRequest.of(0, 10)))
                .extracting(Consumo::getId).containsExactly(reciente.getId());
        var pagina = consumos.findByTarjetaIdAndFechaBetweenOrderByFechaDesc(
                tarjetaId, FECHA.minusSeconds(120), FECHA.plusSeconds(1), PageRequest.of(0, 1));
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina).extracting(Consumo::getId).containsExactly(reciente.getId());
        assertThat(consumos.findByTarjetaIdAndFechaBetweenOrderByFechaDesc(
                tarjetaId, FECHA.minusSeconds(120), FECHA.plusSeconds(1), PageRequest.of(1, 1)))
                .extracting(Consumo::getId).containsExactly(anterior.getId());
        assertThat(consumos.findByTarjetaIdAndFechaAfterOrderByFechaDesc(
                tarjetaId, FECHA.plusSeconds(1), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("Carga todos los repositorios y ordena historiales sin mezclar tarjetas ni usuarios")
    void consultaHistoriales() {
        UUID tarjetaId = guardarTarjeta("10000001").getId();
        UUID otraTarjetaId = guardarTarjeta("10000002").getId();
        Bloqueo anterior = guardarBloqueo(tarjetaId, FECHA.minusSeconds(60), TipoBloqueo.TEMPORAL);
        Bloqueo reciente = guardarBloqueo(tarjetaId, FECHA, TipoBloqueo.DEFINITIVO);
        guardarBloqueo(otraTarjetaId, FECHA, TipoBloqueo.TEMPORAL);
        AvisoViaje avisoAnterior = guardarAviso(tarjetaId, FECHA.minusSeconds(60));
        AvisoViaje avisoReciente = guardarAviso(tarjetaId, FECHA);
        guardarAviso(otraTarjetaId, FECHA);
        AuditoriaTarjeta auditoriaAnterior = guardarAuditoria(tarjetaId, "+5491100000001", FECHA.minusSeconds(60));
        AuditoriaTarjeta auditoriaReciente = guardarAuditoria(null, "+5491100000001", FECHA);
        guardarAuditoria(otraTarjetaId, "+5491100000002", FECHA);
        entityManager.flush();
        entityManager.clear();

        assertThat(bloqueos.findByTarjetaIdOrderByFechaBloqueoDesc(tarjetaId))
                .extracting(Bloqueo::getId).containsExactly(reciente.getId(), anterior.getId());
        assertThat(avisos.findByTarjetaIdOrderByFechaRegistroDesc(tarjetaId))
                .extracting(AvisoViaje::getId).containsExactly(avisoReciente.getId(), avisoAnterior.getId());
        assertThat(auditorias.findByNumeroWhatsappOrderByFechaDesc("+5491100000001"))
                .extracting(AuditoriaTarjeta::getId)
                .containsExactly(auditoriaReciente.getId(), auditoriaAnterior.getId());
        assertThat(bloqueos.findByTarjetaIdOrderByFechaBloqueoDesc(UUID.randomUUID())).isEmpty();
        assertThat(avisos.findByTarjetaIdOrderByFechaRegistroDesc(UUID.randomUUID())).isEmpty();
        assertThat(auditorias.findByNumeroWhatsappOrderByFechaDesc("inexistente")).isEmpty();
    }

    @Test
    @DisplayName("Aísla titulares con igual número y distinto tipo de documento")
    void distingueTipoDeDocumentoDelTitular() {
        Tarjeta dni = guardarTarjeta("10000001");
        Tarjeta pasaporte = guardarTarjeta("10000001");
        pasaporte.setTitularDocumentoTipo("PASAPORTE");
        entityManager.flush();
        entityManager.clear();

        assertThat(tarjetas.findByTitularDocumentoTipoAndTitularDocumentoNumero("DNI", "10000001"))
                .extracting(Tarjeta::getId).containsExactly(dni.getId());
        assertThat(tarjetas.findByTitularDocumentoTipoAndTitularDocumentoNumero("PASAPORTE", "10000001"))
                .extracting(Tarjeta::getId).containsExactly(pasaporte.getId());
        assertThat(tarjetas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                pasaporte.getId(), "DNI", "10000001")).isEmpty();
    }

    @Test
    @DisplayName("Incluye ambos extremos del intervalo de consumos y excluye fechas externas")
    void consultaConsumosEnLimitesDelIntervalo() {
        UUID tarjetaId = guardarTarjeta("10000001").getId();
        Instant hasta = FECHA.plusSeconds(60);
        guardarConsumo(tarjetaId, FECHA.minusSeconds(1));
        Consumo desdeInclusivo = guardarConsumo(tarjetaId, FECHA);
        Consumo hastaInclusivo = guardarConsumo(tarjetaId, hasta);
        guardarConsumo(tarjetaId, hasta.plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        var pagina = consumos.findByTarjetaIdAndFechaBetweenOrderByFechaDesc(
                tarjetaId, FECHA, hasta, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina).extracting(Consumo::getId)
                .containsExactly(hastaInclusivo.getId(), desdeInclusivo.getId());
    }

    @Test
    @DisplayName("Rechaza persistir una tarjeta sin el hash obligatorio")
    void rechazaTarjetaSinHash() {
        Tarjeta tarjeta = guardarTarjeta("10000001");
        tarjeta.setNumeroTarjetaHash(null);

        assertThatThrownBy(() -> tarjetas.saveAndFlush(tarjeta))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Tarjeta guardarTarjeta(String documento) {
        Tarjeta tarjeta = new Tarjeta();
        tarjeta.setNumeroTarjetaHash("hash-sintetico");
        tarjeta.setUltimosCuatro("1234");
        tarjeta.setMarca(MarcaTarjeta.VISA);
        tarjeta.setTipo(TipoTarjeta.CREDITO);
        tarjeta.setTitularDocumentoTipo("DNI");
        tarjeta.setTitularDocumentoNumero(documento);
        tarjeta.setEstado(EstadoTarjeta.ACTIVA);
        tarjeta.setActiva(true);
        return tarjetas.save(tarjeta);
    }

    private Consumo guardarConsumo(UUID tarjetaId, Instant fecha) {
        Consumo consumo = new Consumo();
        consumo.setTarjetaId(tarjetaId);
        consumo.setFecha(fecha);
        consumo.setComercio("Comercio de prueba");
        consumo.setMonto(new BigDecimal("1250.50"));
        consumo.setMoneda("ARS");
        return consumos.save(consumo);
    }

    private Bloqueo guardarBloqueo(UUID tarjetaId, Instant fecha, TipoBloqueo tipo) {
        Bloqueo bloqueo = new Bloqueo();
        bloqueo.setTarjetaId(tarjetaId);
        bloqueo.setTipo(tipo);
        bloqueo.setMotivo(tipo == TipoBloqueo.DEFINITIVO ? "ROBO" : null);
        bloqueo.setFechaBloqueo(fecha);
        bloqueo.setUsuarioEjecutor("10000001");
        bloqueo.setDobleConfirmacionCompleta(tipo == TipoBloqueo.DEFINITIVO);
        return bloqueos.save(bloqueo);
    }

    private AvisoViaje guardarAviso(UUID tarjetaId, Instant fecha) {
        AvisoViaje aviso = new AvisoViaje();
        aviso.setTarjetaId(tarjetaId);
        aviso.setPais("Uruguay");
        aviso.setFechaDesde(LocalDate.of(2026, 11, 1));
        aviso.setFechaHasta(LocalDate.of(2026, 11, 10));
        aviso.setFechaRegistro(fecha);
        aviso.setConfirmado(true);
        return avisos.save(aviso);
    }

    private AuditoriaTarjeta guardarAuditoria(UUID tarjetaId, String numeroWhatsapp, Instant fecha) {
        AuditoriaTarjeta auditoria = new AuditoriaTarjeta();
        auditoria.setAccion(AuditoriaAccion.LISTAR_TARJETAS);
        auditoria.setResultado(AuditoriaResultado.EXITOSO);
        auditoria.setNumeroWhatsapp(numeroWhatsapp);
        auditoria.setDocumentoTipo("DNI");
        auditoria.setDocumentoNumero("10000001");
        auditoria.setTarjetaId(tarjetaId);
        auditoria.setFecha(fecha);
        return auditorias.save(auditoria);
    }
}
