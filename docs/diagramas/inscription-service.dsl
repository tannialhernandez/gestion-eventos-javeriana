workspace "Gestión de Eventos Académicos" "Plataforma de gestión de eventos académicos - Javeriana" {

  model {
    estudiante = person "Estudiante" "Inscribe y gestiona sus participaciones en eventos"
    coordinador = person "Coordinador Académico" "Publica y administra eventos"

    sistema = softwareSystem "Plataforma de Gestión de Eventos Académicos" "Sistema distribuido de microservicios para gestión de eventos" {

      apiGateway = container "API Gateway" "Enrutamiento y autenticación" "Spring Cloud Gateway"
      eventService = container "Event Service" "Gestiona catálogo de eventos" "Spring Boot 3 / Java 17"
      paymentService = container "Payment Service" "Procesa pagos" "Spring Boot 3 / Java 17"
      notificationService = container "Notification Service" "Envía notificaciones" "Spring Boot 3 / Java 17"
      messageBroker = container "Message Broker" "Mensajería asíncrona" "RabbitMQ"

      inscripcionService = container "Inscription Service" "Gestiona el ciclo de vida completo de inscripciones con control de concurrencia" "Spring Boot 3 / Java 17" {

        restController = component "InscripcionRestController" "Driving adapter REST. Expone POST /api/v1/inscripciones, GET y DELETE" "@RestController"
        eventoConsumer = component "EventoConsumerListener" "Driving adapter RabbitMQ. Consume pago.confirmado y pago.expirado" "@Component @RabbitListener"

        crearService = component "CrearInscripcionService" "Crea inscripción con bloqueo pesimista SELECT FOR UPDATE sobre Evento y registra OutboxEvent en misma transacción (ADR-012, ADR-008)" "@Transactional Application Service"
        confirmarService = component "ConfirmarInscripcionService" "Confirma inscripción al recibir pago exitoso" "@Transactional Application Service"
        cancelarService = component "CancelarInscripcionService" "Cancela inscripción aplicando política de cancelación y libera cupo" "@Transactional Application Service"
        expirarService = component "ExpirarInscripcionesPendientesService" "Expira inscripciones pendientes cada 60s con ShedLock (ADR-018)" "@Scheduled @SchedulerLock Application Service"
        outboxRelay = component "OutboxRelayService" "Publica eventos del Outbox a RabbitMQ cada 2s con ShedLock (ADR-008, ADR-018)" "@Scheduled @SchedulerLock Application Service"

        politicaCancelacion = component "PoliticaCancelacion" "Evalúa reglas de negocio para cancelación y reembolso" "Domain Service"

        inscripcionPort = component "InscripcionRepository" "Puerto primario de persistencia de inscripciones" "Port Interface"
        eventoPort = component "EventoRepository" "Puerto de persistencia de eventos con soporte para bloqueo pesimista" "Port Interface"
        outboxPort = component "OutboxRepository" "Puerto de persistencia del Transactional Outbox" "Port Interface"
        publisherPort = component "EventPublisher" "Puerto de publicación de eventos a Message Broker" "Port Interface"

        inscripcionJpa = component "InscripcionJpaAdapter" "Driven adapter JPA que implementa InscripcionRepository" "@Repository Driven Adapter"
        eventoJpa = component "EventoJpaAdapter" "Driven adapter JPA que implementa EventoRepository con PESSIMISTIC_WRITE" "@Repository Driven Adapter"
        outboxJpa = component "OutboxJpaAdapter" "Driven adapter JPA que implementa OutboxRepository" "@Repository Driven Adapter"

        restController -> crearService "invoca"
        restController -> cancelarService "invoca"
        eventoConsumer -> confirmarService "invoca al recibir PagoConfirmadoEvent"
        eventoConsumer -> cancelarService "invoca al recibir PagoExpiradoEvent"

        crearService -> inscripcionPort "persiste Inscripcion"
        crearService -> eventoPort "adquiere bloqueo pesimista y decrementa cupo"
        crearService -> outboxPort "registra InscripcionCreadaEvent"
        confirmarService -> inscripcionPort "actualiza estado a CONFIRMADA"
        confirmarService -> outboxPort "registra InscripcionConfirmadaEvent"
        cancelarService -> inscripcionPort "actualiza estado a CANCELADA"
        cancelarService -> eventoPort "incrementa cupo disponible"
        cancelarService -> outboxPort "registra InscripcionCanceladaEvent"
        cancelarService -> politicaCancelacion "consulta reglas"
        expirarService -> inscripcionPort "busca PENDIENTES expiradas"
        expirarService -> cancelarService "delega cancelación por expiración"
        outboxRelay -> outboxPort "lee lote de eventos pendientes"
        outboxRelay -> publisherPort "publica eventos a RabbitMQ"

        inscripcionJpa -> inscripcionPort "implementa"
        eventoJpa -> eventoPort "implementa"
        outboxJpa -> outboxPort "implementa"
      }
    }

    estudiante -> sistema "usa vía navegador web"
    coordinador -> sistema "gestiona eventos vía navegador web"
  }

  views {
    component inscripcionService "InscripcionServiceC3" "C4 Nivel 3: Componentes del Inscription Service" {
      include *
      autoLayout lr
    }

    systemContext sistema "SystemContext" "C4 Nivel 1: Contexto del sistema" {
      include *
      autoLayout lr
    }

    container sistema "Containers" "C4 Nivel 2: Contenedores del sistema" {
      include *
      autoLayout lr
    }

    theme default
  }
}
