package com.javeriana.eventos.event.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javeriana.eventos.event.domain.model.Evento;
import com.javeriana.eventos.event.domain.port.out.EventoCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de infraestructura: implementa EventoCachePort usando Redis.
 *
 * CQRS — Query Side Cache:
 * - Cada evento publicado se guarda con TTL de 10 minutos.
 * - La lista del catálogo completo se guarda con TTL de 5 minutos.
 * - Al publicar o cancelar un evento, se invalida la entrada correspondiente.
 *
 * La serialización usa JSON para que sea human-readable en debug.
 */
@Component
public class RedisEventoCacheAdapter implements EventoCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisEventoCacheAdapter.class);
    private static final String KEY_EVENTO = "evento:";
    private static final String KEY_CATALOGO = "catalogo:publicados";
    private static final Duration TTL_EVENTO = Duration.ofMinutes(10);
    private static final Duration TTL_CATALOGO = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisEventoCacheAdapter(RedisTemplate<String, String> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Evento> buscarPorId(UUID eventoId) {
        try {
            String key = KEY_EVENTO + eventoId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, EventoCacheDto.class).toDomain());
        } catch (Exception e) {
            log.warn("Cache miss para evento {}: {}", eventoId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Evento> listarPublicados() {
        try {
            String json = redisTemplate.opsForValue().get(KEY_CATALOGO);
            if (json == null) {
                return Collections.emptyList();
            }
            List<EventoCacheDto> dtos = objectMapper.readValue(json,
                new TypeReference<List<EventoCacheDto>>() {});
            return dtos.stream().map(EventoCacheDto::toDomain).toList();
        } catch (Exception e) {
            log.warn("Cache miss para catálogo: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void guardarEnCache(Evento evento) {
        try {
            String key = KEY_EVENTO + evento.getId();
            String json = objectMapper.writeValueAsString(EventoCacheDto.from(evento));
            redisTemplate.opsForValue().set(key, json, TTL_EVENTO);
        } catch (Exception e) {
            log.error("Error guardando evento en cache: {}", e.getMessage());
        }
    }

    @Override
    public void guardarListaEnCache(List<Evento> eventos) {
        try {
            List<EventoCacheDto> dtos = eventos.stream().map(EventoCacheDto::from).toList();
            String json = objectMapper.writeValueAsString(dtos);
            redisTemplate.opsForValue().set(KEY_CATALOGO, json, TTL_CATALOGO);
        } catch (Exception e) {
            log.error("Error guardando catálogo en cache: {}", e.getMessage());
        }
    }

    @Override
    public void invalidarEvento(UUID eventoId) {
        redisTemplate.delete(KEY_EVENTO + eventoId);
    }

    @Override
    public void invalidarCatalogoCompleto() {
        redisTemplate.delete(KEY_CATALOGO);
    }
}
