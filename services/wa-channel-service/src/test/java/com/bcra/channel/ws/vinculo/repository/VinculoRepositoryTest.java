package com.bcra.channel.ws.vinculo.repository;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.MotivoBloqueo;
import com.bcra.channel.ws.vinculo.entity.Preferencias;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class VinculoRepositoryTest {

    private static final String NUMERO = "+5491123456789";
    private static final Instant FECHA = Instant.parse("2026-10-26T10:00:00Z");

    @Test
    @DisplayName("Persiste preferencias, términos, estados y auditoría sin datos en toString")
    void persisteVinculoCompleto(@Autowired VinculoRepository repository, @Autowired TestEntityManager manager) {
        Vinculo vinculo = crearVinculo(NUMERO, VinculoEstado.BLOQUEADO, FECHA);
        vinculo.setMotivoBloqueo(MotivoBloqueo.SOLICITUD_CLIENTE);
        vinculo.setTerminosAceptados(true);
        vinculo.setFechaAceptacionTerminos(FECHA);
        vinculo.setFechaActivacion(FECHA);
        Preferencias preferencias = new Preferencias();
        preferencias.setIdioma("es");
        preferencias.setNotificacionesHabilitadas(true);
        preferencias.setHorarioPreferido("09:00-18:00");
        vinculo.setPreferencias(preferencias);
        repository.saveAndFlush(vinculo);
        manager.clear();

        Vinculo guardado = repository.findById(vinculo.getId()).orElseThrow();
        assertThat(guardado.getNumeroWhatsapp()).isEqualTo(NUMERO);
        assertThat(guardado.getDocumentoTipo()).isEqualTo("DNI");
        assertThat(guardado.getDocumentoNumero()).isEqualTo("12345678");
        assertThat(guardado.getEstado()).isEqualTo(VinculoEstado.BLOQUEADO);
        assertThat(guardado.getMotivoBloqueo()).isEqualTo(MotivoBloqueo.SOLICITUD_CLIENTE);
        assertThat(guardado.isTerminosAceptados()).isTrue();
        assertThat(guardado.getFechaAceptacionTerminos()).isEqualTo(FECHA);
        assertThat(guardado.getFechaActivacion()).isEqualTo(FECHA);
        assertThat(guardado.getFechaUltimoCambioEstado()).isEqualTo(FECHA);
        assertThat(guardado.getPreferencias().getIdioma()).isEqualTo("es");
        assertThat(guardado.getPreferencias().isNotificacionesHabilitadas()).isTrue();
        assertThat(guardado.getPreferencias().getHorarioPreferido()).isEqualTo("09:00-18:00");
        assertThat(guardado.getCreatedAt()).isNotNull();
        Instant creacion = guardado.getCreatedAt();
        Instant actualizacion = guardado.getUpdatedAt();
        guardado.setEstado(VinculoEstado.DESVINCULADO);
        repository.flush();
        assertThat(guardado.getCreatedAt()).isEqualTo(creacion);
        assertThat(guardado.getUpdatedAt()).isAfterOrEqualTo(actualizacion);
        assertThat(guardado.toString()).doesNotContain(NUMERO, "12345678");
    }

    @Test
    @DisplayName("Filtra por número y estado y ordena los vínculos de un documento por fecha descendente")
    void consultaPorNumeroEstadoYDocumento(@Autowired VinculoRepository repository) {
        Vinculo antiguo = repository.save(crearVinculo(NUMERO, VinculoEstado.DESVINCULADO, FECHA));
        Vinculo reciente = repository.save(crearVinculo(NUMERO, VinculoEstado.ACTIVO, FECHA.plusSeconds(60)));
        Vinculo ajeno = crearVinculo("+5491199999999", VinculoEstado.ACTIVO, FECHA);
        ajeno.setDocumentoNumero("87654321");
        repository.saveAndFlush(ajeno);

        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.ACTIVO))
                .containsExactly(reciente);
        assertThat(repository.findByNumeroWhatsappAndEstado(NUMERO, VinculoEstado.BLOQUEADO)).isEmpty();
        assertThat(repository.findByDocumentoTipoAndDocumentoNumeroOrderByFechaUltimoCambioEstadoDesc(
                "DNI", "12345678")).containsExactly(reciente, antiguo);
        assertThat(repository.findByDocumentoTipoAndDocumentoNumeroOrderByFechaUltimoCambioEstadoDesc(
                "PASAPORTE", "12345678")).isEmpty();
    }

    @Test
    @DisplayName("Permite un vínculo pendiente sin documento e inicializa su fecha de estado")
    void persistePendienteSinDocumento(@Autowired VinculoRepository repository, @Autowired TestEntityManager manager) {
        Vinculo pendiente = new Vinculo();
        pendiente.setNumeroWhatsapp(NUMERO);
        pendiente.setEstado(VinculoEstado.PENDIENTE_VERIFICACION);
        repository.saveAndFlush(pendiente);
        manager.clear();

        Vinculo guardado = repository.findById(pendiente.getId()).orElseThrow();
        assertThat(guardado.getFechaUltimoCambioEstado()).isNotNull();
        assertThat(guardado.getDocumentoNumero()).isNull();
        assertThat(guardado.getMotivoBloqueo()).isNull();
        assertThat(guardado.getFechaActivacion()).isNull();
        assertThat(guardado.getPreferencias().getHorarioPreferido()).isNull();
    }

    @Test
    @DisplayName("Cuenta solo los intentos del vínculo y resultado posteriores al límite exclusivo")
    void cuentaIntentosEnVentana(@Autowired VinculoRepository vinculos,
            @Autowired IntentoVerificacionRepository intentos, @Autowired TestEntityManager manager) {
        UUID vinculoId = vinculos.saveAndFlush(crearVinculo(NUMERO, VinculoEstado.ACTIVO, FECHA)).getId();
        UUID otroId = vinculos.saveAndFlush(
                crearVinculo("+5491199999999", VinculoEstado.ACTIVO, FECHA)).getId();
        guardarIntento(intentos, vinculoId, ResultadoIntento.FALLIDO, FECHA.minusSeconds(1));
        guardarIntento(intentos, vinculoId, ResultadoIntento.FALLIDO, FECHA);
        IntentoVerificacion posterior = guardarIntento(
                intentos, vinculoId, ResultadoIntento.FALLIDO, FECHA.plusSeconds(1));
        guardarIntento(intentos, vinculoId, ResultadoIntento.EXITOSO, FECHA.plusSeconds(1));
        guardarIntento(intentos, otroId, ResultadoIntento.FALLIDO, FECHA.plusSeconds(1));
        manager.clear();

        assertThat(intentos.countByVinculoIdAndResultadoAndFechaAfter(vinculoId, ResultadoIntento.FALLIDO, FECHA))
                .isEqualTo(1);
        assertThat(intentos.countByVinculoIdAndResultadoAndFechaAfter(
                vinculoId, ResultadoIntento.FALLIDO, FECHA.plusSeconds(1))).isZero();
        IntentoVerificacion guardado = intentos.findById(posterior.getId()).orElseThrow();
        assertThat(guardado.getVinculoId()).isEqualTo(vinculoId);
        assertThat(guardado.getFecha()).isEqualTo(FECHA.plusSeconds(1));
        assertThat(guardado.getResultado()).isEqualTo(ResultadoIntento.FALLIDO);
    }

    @Test
    @DisplayName("Rechaza un intento cuyo vínculo no existe")
    void rechazaIntentoHuerfano(@Autowired IntentoVerificacionRepository repository) {
        assertThatThrownBy(() -> guardarIntento(repository, UUID.randomUUID(), ResultadoIntento.FALLIDO, FECHA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza números que no cumplen E.164")
    void rechazaNumeroInvalido(@Autowired VinculoRepository repository) {
        assertThatThrownBy(() -> repository.saveAndFlush(crearVinculo("123", VinculoEstado.ACTIVO, FECHA)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("La identidad de las entidades depende del UUID y mantiene estable el hash")
    void comparaIdentidadPersistente() {
        Vinculo primero = new Vinculo();
        Vinculo segundo = new Vinculo();
        assertThat(primero).isEqualTo(primero).isNotEqualTo(segundo).isNotEqualTo("otro");
        int hash = primero.hashCode();
        UUID id = UUID.randomUUID();
        primero.setId(id);
        segundo.setId(id);
        assertThat(primero).isEqualTo(segundo);
        assertThat(primero.hashCode()).isEqualTo(hash).isEqualTo(segundo.hashCode());
        IntentoVerificacion intento = new IntentoVerificacion();
        IntentoVerificacion copia = new IntentoVerificacion();
        assertThat(intento).isEqualTo(intento).isNotEqualTo(copia).isNotEqualTo(primero);
        int hashIntento = intento.hashCode();
        intento.setId(id);
        copia.setId(id);
        assertThat(intento).isEqualTo(copia);
        assertThat(intento.hashCode()).isEqualTo(hashIntento).isEqualTo(copia.hashCode());
    }

    private Vinculo crearVinculo(String numero, VinculoEstado estado, Instant fecha) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(numero);
        vinculo.setDocumentoTipo("DNI");
        vinculo.setDocumentoNumero("12345678");
        vinculo.setEstado(estado);
        vinculo.setFechaUltimoCambioEstado(fecha);
        return vinculo;
    }

    private IntentoVerificacion guardarIntento(IntentoVerificacionRepository repository, UUID vinculoId,
            ResultadoIntento resultado, Instant fecha) {
        IntentoVerificacion intento = new IntentoVerificacion();
        intento.setVinculoId(vinculoId);
        intento.setResultado(resultado);
        intento.setFecha(fecha);
        return repository.saveAndFlush(intento);
    }
}
