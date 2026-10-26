package com.bcra.channel.ws.vinculo.repository;

import java.time.Instant;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bcra.channel.ws.vinculo.entity.IntentoVerificacion;
import com.bcra.channel.ws.vinculo.entity.ResultadoIntento;
import com.bcra.channel.ws.vinculo.entity.Vinculo;
import com.bcra.channel.ws.vinculo.entity.VinculoEstado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class VinculoRestriccionesTest {

    private static final String NUMERO = "+5491123456789";
    private static final Instant FECHA = Instant.parse("2026-10-26T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"+0123456789", "+54 91123456789", "+54911abc", "+1234567890123456", ""})
    @DisplayName("Rechaza números con cero inicial, espacios, letras o longitud inválida")
    void rechazaFormatosInvalidos(String numero, @Autowired VinculoRepository repository) {
        Vinculo vinculo = crearPendiente(numero);

        assertThatThrownBy(() -> repository.saveAndFlush(vinculo))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {NUMERO, "+123456789012345"})
    @DisplayName("Conserva el número internacional incluido el máximo de quince dígitos")
    void conservaNumeroInternacional(String numero, @Autowired VinculoRepository repository,
            @Autowired TestEntityManager entityManager) {
        Vinculo vinculo = repository.saveAndFlush(crearPendiente(numero));
        entityManager.clear();

        assertThat(repository.findById(vinculo.getId()).orElseThrow().getNumeroWhatsapp()).isEqualTo(numero);
    }

    @Test
    @DisplayName("Rechaza intentos sin fecha aunque el vínculo exista")
    void rechazaIntentoSinFecha(@Autowired VinculoRepository vinculos,
            @Autowired IntentoVerificacionRepository intentos) {
        Vinculo vinculo = vinculos.saveAndFlush(crearPendiente(NUMERO));
        IntentoVerificacion intento = new IntentoVerificacion();
        intento.setVinculoId(vinculo.getId());
        intento.setResultado(ResultadoIntento.FALLIDO);

        assertThatThrownBy(() -> intentos.saveAndFlush(intento))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechaza intentos sin resultado aunque el vínculo exista")
    void rechazaIntentoSinResultado(@Autowired VinculoRepository vinculos,
            @Autowired IntentoVerificacionRepository intentos) {
        Vinculo vinculo = vinculos.saveAndFlush(crearPendiente(NUMERO));
        IntentoVerificacion intento = new IntentoVerificacion();
        intento.setVinculoId(vinculo.getId());
        intento.setFecha(FECHA);

        assertThatThrownBy(() -> intentos.saveAndFlush(intento))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Vinculo crearPendiente(String numero) {
        Vinculo vinculo = new Vinculo();
        vinculo.setNumeroWhatsapp(numero);
        vinculo.setEstado(VinculoEstado.PENDIENTE_VERIFICACION);
        return vinculo;
    }
}
