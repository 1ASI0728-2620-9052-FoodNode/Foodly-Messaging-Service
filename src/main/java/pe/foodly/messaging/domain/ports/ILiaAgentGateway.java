package pe.foodly.messaging.domain.ports;

import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Puerto (ACL) mediante el cual el dominio pide interpretar un mensaje sin conocer a n8n ni a OpenAI (5.6.1).
 * La respuesta no es el valor de retorno: llega después por el callback.
 */
public interface ILiaAgentGateway {

    /**
     * @param id id del mensaje del explorador que se quiere interpretar
     * @param content texto del mensaje
     * @param ctx conversación, ubicación e historial
     */
    void requestInterpretation(MessageId id, MessageContent content, ConversationContext ctx);
}
