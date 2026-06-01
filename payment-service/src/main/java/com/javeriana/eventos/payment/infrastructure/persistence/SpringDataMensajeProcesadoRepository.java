package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.infrastructure.persistence.entity.MensajeProcesadoEntity;
import com.javeriana.eventos.payment.infrastructure.persistence.entity.MensajeProcesadoId;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMensajeProcesadoRepository
        extends JpaRepository<MensajeProcesadoEntity, MensajeProcesadoId> {

    boolean existsByIdMessageIdAndIdConsumerGroup(String messageId, String consumerGroup);
}
