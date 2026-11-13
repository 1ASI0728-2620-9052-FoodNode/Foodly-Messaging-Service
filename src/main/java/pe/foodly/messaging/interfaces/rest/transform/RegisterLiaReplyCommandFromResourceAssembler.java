package pe.foodly.messaging.interfaces.rest.transform;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.commands.RequestedReservation;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.PriceRange;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.interfaces.rest.InvalidRequestException;
import pe.foodly.messaging.interfaces.rest.resources.LiaCallbackResource;

/** Convierte el callback del Integration System en el command que registra la respuesta de Lía (5.6.2). */
public final class RegisterLiaReplyCommandFromResourceAssembler {

    private RegisterLiaReplyCommandFromResourceAssembler() {
    }

    /** @throws InvalidRequestException si el dominio rechaza algún valor (p. ej. presupuesto mínimo mayor al máximo) */
    public static RegisterLiaReplyCommand toCommand(LiaCallbackResource resource) {
        try {
            return new RegisterLiaReplyCommand(
                    new ConversationId(resource.conversationId()),
                    new MessageId(resource.inReplyToMessageId()),
                    resource.replyText(),
                    toIntent(resource.intent()),
                    suggestedIds(resource.suggestedHuariques()),
                    toRequestedReservation(resource.requestedReservation()));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage(), e);
        }
    }

    private static UserIntent toIntent(LiaCallbackResource.Intent intent) {
        if (intent == null) {
            return null;
        }
        return new UserIntent(
                intent.type(),
                intent.cravings(),
                intent.budgetRange() == null
                        ? null
                        : new PriceRange(intent.budgetRange().min(), intent.budgetRange().max()),
                intent.location() == null
                        ? null
                        : new GeoCoordinates(intent.location().latitude(), intent.location().longitude()),
                intent.partySize(),
                intent.requestedAt(),
                intent.huariqueId() == null ? null : new HuariqueId(intent.huariqueId()));
    }

    private static RequestedReservation toRequestedReservation(LiaCallbackResource.RequestedReservation reservation) {
        if (reservation == null) {
            return null;
        }
        return new RequestedReservation(
                reservation.huariqueId() == null ? null : new HuariqueId(reservation.huariqueId()),
                reservation.requestedAt(),
                reservation.partySize());
    }

    /**
     * El formato de {@code suggestedHuariques} no está definido: se aceptan ids como texto o como objetos con
     * {@code huariqueId} / {@code id}, y lo que no se entienda se descarta, porque no se usa para decidir nada.
     */
    private static List<HuariqueId> suggestedIds(List<Object> suggested) {
        if (suggested == null) {
            return List.of();
        }
        return suggested.stream()
                .flatMap(item -> {
                    Object raw = item instanceof Map<?, ?> map
                            ? (map.get("huariqueId") != null ? map.get("huariqueId") : map.get("id"))
                            : item;
                    try {
                        return raw == null ? Stream.<HuariqueId>empty() : Stream.of(HuariqueId.of(raw.toString()));
                    } catch (IllegalArgumentException e) {
                        return Stream.<HuariqueId>empty();
                    }
                })
                .toList();
    }
}
