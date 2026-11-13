package pe.foodly.messaging.infrastructure.lia;

/** Cómo se pide a Lía que interprete un mensaje ({@code LIA_GATEWAY_MODE}). */
public enum LiaGatewayMode {
    /** Por defecto (DD-07): la solicitud va al Integration System, que invoca a n8n y reenvía el callback. */
    INTEGRATION_SYSTEM,
    /** Solo desarrollo local: la solicitud va directo al webhook de n8n, que llama al callback de Messaging. */
    DIRECT_N8N,
    /** Sin IA ni servicios externos: clasifica por palabras clave y responde por sí mismo. */
    STUB
}
