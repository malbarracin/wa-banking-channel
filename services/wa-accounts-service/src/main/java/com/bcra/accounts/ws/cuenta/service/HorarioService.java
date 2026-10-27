package com.bcra.accounts.ws.cuenta.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bcra.accounts.ws.cuenta.exception.TransferenciaRechazadaException;

/** Horario operativo con zona, días e intervalo [inicio, fin) configurables. */
@Service
public class HorarioService {

    private final LocalTime inicio;
    private final LocalTime fin;
    private final ZoneId zona;
    private final Set<DayOfWeek> dias;

    public HorarioService(
            @Value("${limits.transferencia.horario-inicio:08:00}") String inicio,
            @Value("${limits.transferencia.horario-fin:17:00}") String fin,
            @Value("${limits.transferencia.zona:America/Argentina/Buenos_Aires}") String zona,
            @Value("${limits.transferencia.dias:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") Set<DayOfWeek> dias) {
        this.inicio = LocalTime.parse(inicio);
        this.fin = LocalTime.parse(fin);
        this.zona = ZoneId.of(zona);
        this.dias = Set.copyOf(dias);
        if (!this.inicio.isBefore(this.fin) || this.dias.isEmpty()) {
            throw new IllegalArgumentException("Horario bancario inválido");
        }
    }

    /** Rechaza operaciones fuera de días e intervalo permitidos en la zona del banco. */
    public void validar(Instant ahora) {
        var fecha = ahora.atZone(zona);
        if (!dias.contains(fecha.getDayOfWeek()) || fecha.toLocalTime().isBefore(inicio)
                || !fecha.toLocalTime().isBefore(fin)) {
            throw new TransferenciaRechazadaException("Horario permitido: " + dias + " " + inicio + "–" + fin
                    + " (" + zona + ")");
        }
    }

    public ZoneId zona() {
        return zona;
    }
}

