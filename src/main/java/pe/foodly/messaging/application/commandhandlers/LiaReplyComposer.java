package pe.foodly.messaging.application.commandhandlers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;

/**
 * Arma el texto final de cada respuesta de Lía: lo que redactó n8n ({@code replyText}) más los datos
 * que Messaging obtuvo de Radar, Business o Reservation. Todo en español, claro y conversacional. El
 * resultado nunca supera la longitud máxima del chat.
 */
final class LiaReplyComposer {

    static final int MAX_MENU_ITEMS = 10;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Qué pedir al explorador por cada nombre que devuelve {@code UserIntent.missingContext()}. */
    private static final Map<String, String> MISSING = Map.of(
            "location", "tu ubicación",
            "huariqueId", "a qué huarique te refieres",
            "requestedAt", "para qué fecha y hora",
            "partySize", "para cuántas personas",
            "type", "si quieres una recomendación, información de un huarique o hacer una reserva");

    private static final String MANUAL_RESERVATION =
            "Si lo prefieres, puedes hacer tu reserva de forma manual desde la ficha del huarique.";

    private final int maxLength;

    LiaReplyComposer(int maxLength) {
        this.maxLength = maxLength;
    }

    /** Un huarique recomendado: nombre (de Business) y distancia (de Radar). */
    record Recommended(String name, double distanceMeters) {
    }

    String recommendations(String replyText, List<Recommended> items) {
        if (items.isEmpty()) {
            return compose(replyText, "Por ahora no encontré huariques cerca de tu ubicación. "
                    + "Prueba de nuevo más tarde o desde otra zona.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Esto es lo que encontré cerca de ti:");
        for (int i = 0; i < items.size(); i++) {
            Recommended item = items.get(i);
            lines.add((i + 1) + ". " + item.name() + " (a " + distance(item.distanceMeters()) + ")");
        }
        return compose(replyText, String.join("\n", lines));
    }

    String huariqueNotFound(String replyText) {
        return compose(replyText, "No encontré información de ese huarique. Verifica que sea el correcto.");
    }

    String huariqueInfo(String replyText, HuariqueDetails details, List<MenuItem> menu) {
        List<String> lines = new ArrayList<>();
        lines.add(details.name());
        addIfPresent(lines, details.description());
        if (details.address() != null && !details.address().isBlank()) {
            lines.add("Dirección: " + details.address());
        }
        if (details.openingHours() != null && !details.openingHours().isBlank()) {
            lines.add("Horario: " + details.openingHours());
        }
        if (details.open() != null) {
            lines.add(details.open() ? "Ahora está abierto." : "Ahora está cerrado.");
        }
        if (menu.isEmpty()) {
            lines.add("Todavía no tiene una carta publicada.");
        } else {
            lines.add("Carta:");
            menu.stream().limit(MAX_MENU_ITEMS).forEach(item -> lines.add("- " + item.name() + price(item.price())));
            if (menu.size() > MAX_MENU_ITEMS) {
                lines.add("…y " + (menu.size() - MAX_MENU_ITEMS) + " platos más.");
            }
        }
        return compose(replyText, String.join("\n", lines));
    }

    String reservationRequested(
            String replyText, ReservationConfirmation confirmation, LocalDateTime requestedAt, int partySize) {
        String people = partySize == 1 ? "1 persona" : partySize + " personas";
        String text = "Envié tu solicitud de reserva para " + people + " el " + requestedAt.format(DATE)
                + " a las " + requestedAt.format(TIME) + ". Estado: " + status(confirmation.status()) + ".";
        return compose(replyText, text);
    }

    String reservationRejected(String reason) {
        String why = reason == null || reason.isBlank() ? "no pudieron aceptar tu solicitud" : reason.strip();
        return compose(null, "No pude registrar tu reserva: " + stripTrailingDot(why) + ". "
                + "Puedes probar con otra fecha u hora. " + MANUAL_RESERVATION);
    }

    String clarification(String replyText, List<String> missingContext) {
        List<String> asks = missingContext.stream()
                .map(name -> MISSING.getOrDefault(name, name))
                .collect(Collectors.toList());
        String question = "Para continuar necesito que me indiques " + joinWithY(asks) + ".";
        return compose(replyText, question);
    }

    /** Respuesta amable cuando un proveedor falla, se cae por timeout o el token ya no está. */
    String failure(boolean wasReservation) {
        String text = "Lo siento, no pude completar tu solicitud en este momento. "
                + "Por favor, inténtalo de nuevo en unos minutos.";
        return compose(null, wasReservation ? text + " " + MANUAL_RESERVATION : text);
    }

    /** Aviso cuando Lía no responde dentro del tiempo de espera: siempre ofrece la reserva manual (RF-15). */
    String timeout() {
        return compose(null, "Lo siento, Lía está tardando más de lo esperado en responderte. "
                + "Puedes volver a escribirme en unos minutos. " + MANUAL_RESERVATION);
    }

    private String compose(String replyText, String data) {
        String text = replyText == null || replyText.isBlank() ? data : replyText.strip() + "\n\n" + data;
        return fit(text);
    }

    private String fit(String text) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = maxLength - 1;
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--; // no partir un carácter formado por dos unidades
        }
        return text.substring(0, end) + "…";
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String price(BigDecimal price) {
        return price == null ? "" : String.format(Locale.ROOT, ": S/ %.2f", price);
    }

    private static String distance(double meters) {
        return meters < 1000
                ? Math.round(meters) + " m"
                : String.format(Locale.ROOT, "%.1f km", meters / 1000);
    }

    private static String status(String status) {
        if (status == null || status.isBlank()) {
            return "pendiente";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "pendiente de aprobación del dueño";
            case "ACCEPTED", "CONFIRMED" -> "aceptada";
            case "REJECTED" -> "rechazada";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    private static String joinWithY(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    private static String stripTrailingDot(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
