package com.bcra.insurance.ws.seguro.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.bcra.insurance.ws.seguro.entity.Cobertura;
import com.bcra.insurance.ws.seguro.entity.EstadoSiniestro;
import com.bcra.insurance.ws.seguro.entity.Exclusion;
import com.bcra.insurance.ws.seguro.entity.Poliza;
import com.bcra.insurance.ws.seguro.entity.Siniestro;
import com.bcra.insurance.ws.seguro.entity.TipoSiniestro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SeguroRepositoryTest {

    private static final Instant APERTURA = Instant.parse("2026-10-27T12:00:00Z");
    private static final List<EstadoSiniestro> ABIERTOS = List.of(
            EstadoSiniestro.RECIBIDO, EstadoSiniestro.EN_ANALISIS, EstadoSiniestro.DOCUMENTACION_PENDIENTE);

    @Autowired
    private PolizaRepository polizas;
    @Autowired
    private CoberturaRepository coberturas;
    @Autowired
    private ExclusionRepository exclusiones;
    @Autowired
    private SiniestroRepository siniestros;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Lista solamente pólizas vigentes del tipo y número de documento indicado")
    void filtraPolizasPorTitularYVigencia() {
        Poliza propia = polizas.saveAndFlush(poliza("P-1", "DNI", "12345678", true));
        polizas.save(poliza("P-2", "DNI", "12345678", false));
        polizas.save(poliza("P-3", "DNI", "87654321", true));
        polizas.save(poliza("P-4", "PASAPORTE", "12345678", true));
        entityManager.flush();
        entityManager.clear();

        List<Poliza> encontradas = polizas
                .findByTitularDocumentoTipoAndTitularDocumentoNumeroAndVigenteTrue("DNI", "12345678");

        assertThat(encontradas).extracting(Poliza::getId).containsExactly(propia.getId());
        Poliza recuperada = encontradas.getFirst();
        assertThat(recuperada.getRamo()).isEqualTo("HOGAR");
        assertThat(recuperada.getFechaVigenciaDesde()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(recuperada.getFechaVigenciaHasta()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(recuperada.getCreatedAt()).isNotNull();
        assertThat(recuperada.getUpdatedAt()).isNotNull();
        assertThat(polizas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                propia.getId(), "DNI", "12345678")).isPresent();
        assertThat(polizas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                propia.getId(), "DNI", "87654321")).isEmpty();
        assertThat(polizas.findByIdAndTitularDocumentoTipoAndTitularDocumentoNumero(
                propia.getId(), "PASAPORTE", "12345678")).isEmpty();
        assertThat(polizas.findByTitularDocumentoTipoAndTitularDocumentoNumeroAndVigenteTrue(
                "DNI", "sin-registro")).isEmpty();
    }

    @Test
    @DisplayName("Coberturas y exclusiones se recuperan únicamente para su póliza")
    void consultaCondicionesDeLaPoliza() {
        Poliza propia = polizas.save(poliza("P-1", "DNI", "12345678", true));
        Poliza otra = polizas.save(poliza("P-2", "DNI", "87654321", true));
        Cobertura cobertura = coberturas.save(Cobertura.builder().poliza(propia)
                .descripcion("Condiciones oficiales").resumen("Incendio del hogar").build());
        Exclusion exclusion = exclusiones.save(Exclusion.builder().poliza(propia)
                .descripcion("Daño intencional").build());
        coberturas.save(Cobertura.builder().poliza(otra).descripcion("Otra").resumen("Otra").build());
        exclusiones.save(Exclusion.builder().poliza(otra).descripcion("Otra").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(coberturas.findByPolizaId(propia.getId())).singleElement().satisfies(encontrada -> {
            assertThat(encontrada.getId()).isEqualTo(cobertura.getId());
            assertThat(encontrada.getDescripcion()).isEqualTo("Condiciones oficiales");
            assertThat(encontrada.getResumen()).isEqualTo("Incendio del hogar");
            assertThat(encontrada.getPoliza().getId()).isEqualTo(propia.getId());
        });
        assertThat(exclusiones.findByPolizaId(propia.getId())).singleElement().satisfies(encontrada -> {
            assertThat(encontrada.getId()).isEqualTo(exclusion.getId());
            assertThat(encontrada.getDescripcion()).isEqualTo("Daño intencional");
            assertThat(encontrada.getPoliza().getId()).isEqualTo(propia.getId());
        });
        assertThat(coberturas.findByPolizaId(UUID.randomUUID())).isEmpty();
        assertThat(exclusiones.findByPolizaId(UUID.randomUUID())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(EstadoSiniestro.class)
    @DisplayName("El filtro de duplicados respeta estados abiertos, tipo, póliza y límite temporal")
    void filtraDuplicados(EstadoSiniestro estado) {
        UUID polizaId = polizas.save(poliza("P-1", "DNI", "12345678", true)).getId();
        Siniestro candidato = siniestros.save(siniestro("C-1", polizaId, TipoSiniestro.HOGAR, estado, APERTURA));
        siniestros.save(siniestro("C-2", polizaId, TipoSiniestro.AUTO, estado, APERTURA));
        siniestros.save(siniestro("C-3", UUID.randomUUID(), TipoSiniestro.HOGAR, estado, APERTURA));
        siniestros.save(siniestro("C-4", polizaId, TipoSiniestro.HOGAR, estado, APERTURA.minusSeconds(60)));
        entityManager.flush();
        entityManager.clear();

        List<Siniestro> encontrados = siniestros
                .findByPolizaIdAndTipoSiniestroAndEstadoInAndFechaAperturaAfter(
                        polizaId, TipoSiniestro.HOGAR, ABIERTOS, APERTURA.minusSeconds(60));

        assertThat(encontrados).extracting(Siniestro::getId)
                .containsExactlyElementsOf(estado == EstadoSiniestro.RESUELTO ? List.of() : List.of(candidato.getId()));
        Siniestro recuperado = siniestros.findByNumeroCaso("C-1").orElseThrow();
        assertThat(recuperado.getEstado()).isEqualTo(estado);
        assertThat(recuperado.getTipoSiniestro()).isEqualTo(TipoSiniestro.HOGAR);
        assertThat(recuperado.getFechaSiniestro()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(recuperado.getDescripcion()).isEqualTo("Daño en el hogar");
        assertThat(recuperado.getFechaApertura()).isEqualTo(APERTURA);
        assertThat(recuperado.getVentanaDuplicadoHasta()).isEqualTo(APERTURA.plusSeconds(3600));
        assertThat(siniestros.findByNumeroCaso("inexistente")).isEmpty();
    }

    @Test
    @DisplayName("El número de póliza es único en la base")
    void rechazaNumeroPolizaDuplicado() {
        polizas.saveAndFlush(poliza("P-1", "DNI", "12345678", true));

        assertThatThrownBy(() -> polizas.saveAndFlush(poliza("P-1", "DNI", "87654321", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El número de caso es único incluso entre pólizas diferentes")
    void rechazaNumeroCasoDuplicado() {
        siniestros.saveAndFlush(siniestro(
                "C-1", UUID.randomUUID(), TipoSiniestro.HOGAR, EstadoSiniestro.RECIBIDO, APERTURA));

        assertThatThrownBy(() -> siniestros.saveAndFlush(siniestro(
                "C-1", UUID.randomUUID(), TipoSiniestro.AUTO, EstadoSiniestro.RECIBIDO, APERTURA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Poliza poliza(String numero, String tipoDocumento, String documento, boolean vigente) {
        return Poliza.builder().numeroPoliza(numero).ramo("HOGAR").vigente(vigente)
                .fechaVigenciaDesde(LocalDate.of(2026, 1, 1)).fechaVigenciaHasta(LocalDate.of(2026, 12, 31))
                .titularDocumentoTipo(tipoDocumento).titularDocumentoNumero(documento).build();
    }

    private Siniestro siniestro(String numero, UUID polizaId, TipoSiniestro tipo, EstadoSiniestro estado,
            Instant apertura) {
        return Siniestro.builder().numeroCaso(numero).polizaId(polizaId).tipoSiniestro(tipo)
                .fechaSiniestro(LocalDate.of(2026, 10, 26)).descripcion("Daño en el hogar").estado(estado)
                .fechaApertura(apertura).ventanaDuplicadoHasta(apertura.plusSeconds(3600)).build();
    }
}
