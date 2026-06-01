package com.javeriana.eventos.inscription.infrastructure.idempotencia;

import com.javeriana.eventos.inscription.domain.port.out.MensajeProcesadoRepository;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA que implementa el puerto MensajeProcesadoRepository.
 *
 * Ejemplo de uso en PagoConfirmadoConsumer:
 *
 *   {@code @RabbitListener(queues = "pago.confirmado")}
 *   {@code @Transactional}
 *   public void onPagoConfirmado(String json,
 *       {@code @Header(AmqpHeaders.MESSAGE_ID)} String messageId) {
 *
 *       if (mensajeProcesadoRepo.estaProcesado(messageId, "confirmar-inscripcion")) {
 *           log.info("Duplicado ignorado: messageId={}", messageId);
 *           return;
 *       }
 *       // ... lógica de negocio ...
 *       mensajeProcesadoRepo.registrarProcesado(messageId,
 *                                               "confirmar-inscripcion",
 *                                               "PAGO_CONFIRMADO");
 *   }
 */
@Repository
public class JpaMensajeProcesadoRepository implements MensajeProcesadoRepository {

    private final SpringDataMensajeProcesadoRepository springDataRepo;

    public JpaMensajeProcesadoRepository(SpringDataMensajeProcesadoRepository springDataRepo) {
        this.springDataRepo = springDataRepo;
    }

    @Override
    public boolean estaProcesado(String messageId, String consumerGrupo) {
        return springDataRepo.existsByIdMessageIdAndIdConsumerGrupo(messageId, consumerGrupo);
    }

    @Override
    public void registrarProcesado(String messageId, String consumerGrupo,
                                    String tipoMensaje) {
        springDataRepo.save(
            new MensajeProcesadoEntity(messageId, consumerGrupo, tipoMensaje));
    }
}
