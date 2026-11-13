package pe.foodly.messaging.infrastructure.lia;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * «IA» del modo {@code stub}: clasifica el mensaje con palabras clave, sin ninguna llamada externa.
 *
 * <ul>
 *   <li>{@code reserva}, {@code mesa} → RESERVATION_REQUEST;</li>
 *   <li>{@code menú}, {@code precio}, {@code horario} → HUARIQUE_INQUIRY;</li>
 *   <li>{@code cerca}, {@code recomienda}, {@code antojo} → RECOMMENDATION_REQUEST.</li>
 * </ul>
 * Si varias coinciden, gana la primera de esa lista (la acción más específica). Ignora mayúsculas y tildes.
 *
 * <p>Para que se pueda probar una conversación completa extrae lo básico del texto: un UUID como huarique
 * («el menú de a1111111-1111-4111-8111-111111111111»), «N personas» o «para N», «hoy», «mañana» o una fecha
 * {@code yyyy-MM-dd}, y una hora («a las 8 pm», «20:00»). Lo que falte lo pide Messaging con su pregunta de
 * aclaración, y la respuesta siguiente, sin palabras clave, continúa la intención pendiente.
 */
final class StubIntentInterpreter {

    /** Qué responde el stub: el texto de Lía y la intención que «interpretó». */
    record Interpretation(String replyText, UserIntent intent) {
    }

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern PARTY_WORD = Pattern.compile(
            "\\b(\\d{1,2}|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\s+(?:personas?|pax|comensales)\\b");
    private static final Pattern PARTY_PARA = Pattern.compile("\\bpara\\s+(\\d{1,2})\\b(?!\\s*(?::|am\\b|pm\\b|h\\b|hrs\\b))");
    /** Grupos: 1 «a las», 2 hora, 3 minutos, 4 sufijo (am, pm, de la mañana/tarde/noche, h). */
    private static final Pattern TIME = Pattern.compile(
            "(a\\s+las?\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am\\b|pm\\b|de la manana|de la tarde|de la noche|hrs\\b|h\\b)?");
    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("una", 1), Map.entry("dos", 2), Map.entry("tres", 3),
            Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6), Map.entry("siete", 7),
            Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10));

    private final Clock clock;

    StubIntentInterpreter(Clock clock) {
        this.clock = clock;
    }

    Interpretation interpret(String content, GeoCoordinates location, List<Message> history) {
        String text = normalize(content);
        UserIntent pending = pendingIntent(history);

        IntentType type = classify(text);
        if (type == null) {
            type = pending != null ? pending.type() : IntentType.UNKNOWN;
        }
        // Solo se continúa una intención pendiente del mismo tipo.
        UserIntent previous = pending != null && pending.type() == type ? pending : null;

        UserIntent intent = switch (type) {
            case RECOMMENDATION_REQUEST -> new UserIntent(type, null, null,
                    location != null ? location : previous == null ? null : previous.location(),
                    null, null, null);
            case HUARIQUE_INQUIRY -> new UserIntent(type, null, null, null, null, null,
                    huariqueId(content, previous, history));
            case RESERVATION_REQUEST -> {
                Integer partySize = partySize(text);
                LocalDateTime requestedAt = requestedAt(text);
                yield new UserIntent(type, null, null, null,
                        partySize != null ? partySize : previous == null ? null : previous.partySize(),
                        requestedAt != null ? requestedAt : previous == null ? null : previous.requestedAt(),
                        huariqueId(content, previous, history));
            }
            case UNKNOWN -> new UserIntent(IntentType.UNKNOWN, null, null, null, null, null, null);
        };
        return new Interpretation(replyFor(type), intent);
    }

    private static IntentType classify(String text) {
        if (containsAny(text, "reserva", "mesa")) {
            return IntentType.RESERVATION_REQUEST;
        }
        if (containsAny(text, "menu", "precio", "horario")) {
            return IntentType.HUARIQUE_INQUIRY;
        }
        if (containsAny(text, "cerca", "recomienda", "antojo")) {
            return IntentType.RECOMMENDATION_REQUEST;
        }
        return null;
    }

    private static String replyFor(IntentType type) {
        return switch (type) {
            case RECOMMENDATION_REQUEST -> "¡Claro! Te busco opciones cerca de ti…";
            case HUARIQUE_INQUIRY -> "Con gusto, te cuento lo que sé de ese huarique.";
            case RESERVATION_REQUEST -> "¡Con gusto! Te ayudo con tu reserva.";
            case UNKNOWN -> "No estoy segura de haberte entendido.";
        };
    }

    /** La intención incompleta de la última respuesta de Lía: lo que se le pidió aclarar al explorador. */
    private static UserIntent pendingIntent(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            if (message.isFromLia()) {
                return message.getIntent()
                        .filter(intent -> intent.type() != IntentType.UNKNOWN && !intent.isComplete())
                        .orElse(null);
            }
        }
        return null;
    }

    /** El UUID del mensaje; si no hay, el de la intención pendiente; si no, el último que mencionó el explorador. */
    private static HuariqueId huariqueId(String content, UserIntent previous, List<Message> history) {
        HuariqueId fromContent = firstUuid(content);
        if (fromContent != null) {
            return fromContent;
        }
        if (previous != null && previous.huariqueId() != null) {
            return previous.huariqueId();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!history.get(i).isFromLia()) {
                HuariqueId found = firstUuid(history.get(i).getContent().text());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static HuariqueId firstUuid(String text) {
        Matcher matcher = UUID.matcher(text);
        return matcher.find() ? HuariqueId.of(matcher.group()) : null;
    }

    private static Integer partySize(String text) {
        Matcher words = PARTY_WORD.matcher(text);
        if (words.find()) {
            return toNumber(words.group(1));
        }
        Matcher para = PARTY_PARA.matcher(text);
        return para.find() ? toNumber(para.group(1)) : null;
    }

    private static Integer toNumber(String token) {
        Integer number = NUMBER_WORDS.get(token);
        if (number == null) {
            number = Integer.valueOf(token);
        }
        return number >= 1 ? number : null;
    }

    private LocalDateTime requestedAt(String text) {
        LocalDate today = LocalDate.now(clock.withZone(LIMA));
        LocalDate date = null;
        String withoutMorning = text.replace("de la manana", " ");
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                date = LocalDate.parse(iso.group(1));
            } catch (DateTimeParseException ignored) {
                // fecha imposible (2026-13-45): se trata como si faltara
            }
        } else if (withoutMorning.contains("pasado manana")) {
            date = today.plusDays(2);
        } else if (withoutMorning.contains("manana")) {
            date = today.plusDays(1);
        } else if (text.contains("hoy")) {
            date = today;
        }
        LocalTime time = time(text);
        return date == null || time == null ? null : date.atTime(time);
    }

    /** La primera hora «segura»: con «a las», con minutos (20:00) o con sufijo (8 pm). Un número suelto no cuenta. */
    private static LocalTime time(String text) {
        String withoutParty = PARTY_PARA.matcher(PARTY_WORD.matcher(text).replaceAll(" ")).replaceAll(" ");
        String withoutDate = ISO_DATE.matcher(withoutParty).replaceAll(" ");
        Matcher matcher = TIME.matcher(withoutDate);
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String minutes = matcher.group(3);
            String suffix = matcher.group(4);
            if (prefix == null && minutes == null && suffix == null) {
                continue;
            }
            int hour = Integer.parseInt(matcher.group(2));
            int minute = minutes == null ? 0 : Integer.parseInt(minutes);
            if (suffix != null && (suffix.startsWith("pm") || suffix.contains("tarde") || suffix.contains("noche"))
                    && hour < 12) {
                hour += 12;
            } else if (suffix != null && (suffix.startsWith("am") || suffix.contains("manana")) && hour == 12) {
                hour = 0;
            }
            if (hour > 23 || minute > 59) {
                continue;
            }
            return LocalTime.of(hour, minute);
        }
        return null;
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Minúsculas y sin tildes, para comparar «menú» con «menu». */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
