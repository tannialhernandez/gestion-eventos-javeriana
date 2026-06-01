package com.javeriana.eventos.payment.infrastructure.persistence;

import com.javeriana.eventos.payment.domain.port.out.MensajeProcesadoRepository;
import com.javeriana.eventos.payment.infrastructure.persistence.entity.MensajeProcesadoEntity;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA para el puerto MensajeProcesadoRepository.
 *
 * Uso esperado en un futuro @RabbitListener:
 *
 *   @RabbitListener(queues = "inscripcion.creada")
 *   @Transactional
 *   public void onInscripcionCreada(String payload, Message message) {
 *       String messageId = message.getMessageProperties().getMessageId();
 *
 *       if (mensajeProcesadoRepo.estaProcesado(messageId, "crear-preferencia")) {
 *           log.info("Mensaje duplicado ignorado: messageId={}", messageId);
 *           return;
 *       }
 *
 *       // ... lógica de negocio ...
 *
 *       mensajeProcesadoRepo.registrarProcesado(messageId, "crear-preferencia",
 *                                                "INSCRIPCION_CREADA");
 *   }
 */
@Repository
public class JpaMensajeProcesadoRepository implements MensajeProcesadoRepository {

    private final SpringDataMensajeProcesadoRepository springDataRepo;

    public JpaMensajeProcesadoRepository(SpringDataMensajeProcesadoRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public boolean estaProcesado(String messageId, String consumerGroup) {
        return springDataRepo.existsByIdMessageIdAndIdConsumerGroup(messageId, consumerGroup);
    }

    @Override
    public void registrarProcesado(String messageId, String consumerGroup, String eventType) {
        springDataRepo.save(new MensajeProcesadoEntity(messageId, consumerGroup, eventType));
    }
}
