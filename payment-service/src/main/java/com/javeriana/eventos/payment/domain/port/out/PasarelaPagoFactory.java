package com.javeriana.eventos.payment.domain.port.out;

/**
 * Factory Method (GoF — Creacional) para la pasarela de pago.
 *
 * El dominio declara QUÉ necesita (un método de fábrica que devuelve un
 * PasarelaPagoPort). La infraestructura decide CUÁL adaptador instanciar,
 * respetando el principio OCP: añadir un nuevo proveedor de pago solo
 * requiere una nueva implementación de PasarelaPagoPort y actualizar la
 * fábrica, sin tocar CrearPreferenciaService.
 *
 * Implementación concreta: DefaultPasarelaPagoFactory (infrastructure/pasarela).
 */
public interface PasarelaPagoFactory {

    /**
     * Retorna el adaptador de pasarela configurado para este despliegue.
     * La selección se basa en la propiedad payment.gateway.provider del
     * application.yml (mercadopago | simulador).
     */
    PasarelaPagoPort crearPasarela();
}
