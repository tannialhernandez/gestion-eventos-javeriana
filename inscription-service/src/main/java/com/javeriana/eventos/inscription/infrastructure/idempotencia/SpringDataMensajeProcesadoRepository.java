package com.javeriana.eventos.inscription.infrastructure.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMensajeProcesadoRepository
        extends JpaRepository<MensajeProcesadoEntity, MensajeProcesadoId> {

    boolean existsByIdMessageIdAndIdConsumerGrupo(String messageId, String consumerGrupo);
}
