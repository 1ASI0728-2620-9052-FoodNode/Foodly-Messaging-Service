package pe.foodly.messaging.infrastructure.lia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.infrastructure.lia.StubIntentInterpreter.Interpretation;

class StubIntentInterpreterTest {

    private static final String FARO = "a1111111-1111-4111-8111-111111111111";
    private static final String ANTICUCHOS = "a2222222-2222-4222-8222-222222222222";
    private static final GeoCoordinates MIRAFLORES = new GeoCoordinates(-12.1219, -77.0297);

    /** 15:00 UTC = 10:00 en Lima del 13/11/2026: «mañana» es el 14/11/2026. */
    private final StubIntentInterpreter interpreter = new StubIntentInterpreter(
            Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneOffset.UTC));

    private Interpretation interpret(String text) {
        return interpreter.interpret(text, MIRAFLORES, List.of());
    }

    // ---- clasificación por palabras clave ----

    @Test
    void cercaRecomiendaYAntojoSonUnaRecomendacionConLaUbicacionDelExplorador() {
        for (String text : List.of("quiero algo cerca", "Recomiéndame un lugar", "tengo un ANTOJO de ceviche")) {
            Interpretation result = interpret(text);

            assertThat(result.intent().type()).as(text).isEqualTo(IntentType.RECOMMENDATION_REQUEST);
            assertThat(result.intent().location()).as(text).isEqualTo(MIRAFLORES);
            assertThat(result.replyText()).startsWith("¡Claro!");
        }
    }

    @Test
    void unaRecomendacionSinUbicacionLaDejaIncompletaParaQueMessagingLaPida() {
        UserIntent intent = interpreter.interpret("quiero algo cerca", null, List.of()).intent();

        assertThat(intent.location()).isNull();
        assertThat(intent.missingContext()).containsExactly("location");
    }

    @Test
    void menuPrecioYHorarioSonUnaConsultaAunConTildesYMayusculas() {
        for (String text : List.of("¿Cuál es el MENÚ?", "qué precio tiene", "a qué hora el horario")) {
            assertThat(interpret(text).intent().type()).as(text).isEqualTo(IntentType.HUARIQUE_INQUIRY);
        }
    }

    @Test
    void laConsultaTomaElHuariqueDelUuidDelMensaje() {
        UserIntent intent = interpret("el menú de " + FARO.toUpperCase() + " por favor").intent();

        assertThat(intent.huariqueId()).isEqualTo(HuariqueId.of(FARO));
        assertThat(intent.isComplete()).isTrue();
    }

    @Test
    void laConsultaSinHuariqueQuedaIncompleta() {
        assertThat(interpret("cuál es el horario").intent().missingContext()).containsExactly("huariqueId");
    }

    @Test
    void reservarYMesaSonUnaReserva() {
        for (String text : List.of("quiero reservar", "una mesa por favor", "hago una Reserva")) {
            assertThat(interpret(text).intent().type()).as(text).isEqualTo(IntentType.RESERVATION_REQUEST);
        }
    }

    @Test
    void siCoincidenVariasGanaLaAccionMasEspecifica() {
        assertThat(interpret("reservar una mesa cerca").intent().type()).isEqualTo(IntentType.RESERVATION_REQUEST);
        assertThat(interpret("el menú de algo cerca").intent().type()).isEqualTo(IntentType.HUARIQUE_INQUIRY);
    }

    @Test
    void sinPalabrasClaveEsDesconocido() {
        Interpretation result = interpret("hola, buenas tardes");

        assertThat(result.intent().type()).isEqualTo(IntentType.UNKNOWN);
        assertThat(result.intent().isComplete()).isFalse();
        assertThat(result.replyText()).contains("No estoy segura");
    }

    // ---- reserva: lo que se extrae del texto ----

    @Test
    void unaReservaCompletaSeExtraeDeUnSoloMensaje() {
        UserIntent intent = interpret("quiero reservar una mesa en " + FARO + " para 4 personas mañana a las 8 pm").intent();

        assertThat(intent.type()).isEqualTo(IntentType.RESERVATION_REQUEST);
        assertThat(intent.huariqueId()).isEqualTo(HuariqueId.of(FARO));
        assertThat(intent.partySize()).isEqualTo(4);
        assertThat(intent.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 20, 0));
        assertThat(intent.isComplete()).isTrue();
    }

    @Test
    void laCantidadDePersonasAceptaVariasFormas() {
        assertThat(interpret("mesa para 6").intent().partySize()).isEqualTo(6);
        assertThat(interpret("mesa para 3 personas").intent().partySize()).isEqualTo(3);
        assertThat(interpret("mesa para dos personas").intent().partySize()).isEqualTo(2);
        assertThat(interpret("mesa para una persona").intent().partySize()).isEqualTo(1);
        assertThat(interpret("mesa, somos 5 comensales").intent().partySize()).isEqualTo(5);
        assertThat(interpret("quiero una mesa").intent().partySize()).isNull();
    }

    @Test
    void laHoraNoSeConfundeConLaCantidadDePersonas() {
        UserIntent intent = interpret("mesa para 8 pm, somos 4 personas hoy").intent();

        assertThat(intent.partySize()).isEqualTo(4);
        assertThat(intent.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 20, 0));
    }

    @Test
    void laFechaAceptaHoyMananaPasadoMananaEIso() {
        assertThat(interpret("mesa hoy 20:00").intent().requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 20, 0));
        assertThat(interpret("mesa mañana 19:30").intent().requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 19, 30));
        assertThat(interpret("mesa pasado mañana a las 21").intent().requestedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 21, 0));
        assertThat(interpret("mesa 2026-12-24 a las 9 de la noche").intent().requestedAt())
                .isEqualTo(LocalDateTime.of(2026, 12, 24, 21, 0));
    }

    @Test
    void laHoraAceptaAmPmYElPrefijoDeLaMananaNoCuentaComoFecha() {
        assertThat(interpret("mesa mañana 12 am").intent().requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 0, 0));
        assertThat(interpret("mesa mañana 12 pm").intent().requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 12, 0));
        // «de la mañana» es la hora del día, no «mañana» (el día siguiente).
        assertThat(interpret("mesa hoy a las 8 de la mañana").intent().requestedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 13, 8, 0));
        assertThat(interpret("mesa a las 8 de la mañana").intent().requestedAt()).isNull();
    }

    @Test
    void sinFechaOSinHoraLaFechaQuedaPendiente() {
        assertThat(interpret("mesa mañana").intent().requestedAt()).isNull();
        assertThat(interpret("mesa a las 8 pm").intent().requestedAt()).isNull();
        assertThat(interpret("mesa mañana 8").intent().requestedAt()).as("un número suelto no es una hora").isNull();
        assertThat(interpret("mesa 2026-13-45 20:00").intent().requestedAt()).isNull();
        assertThat(interpret("mesa hoy a las 25:00").intent().requestedAt()).isNull();
    }

    @Test
    void sinFechaLaReservaPideSoloLoQueFalta() {
        UserIntent intent = interpret("mesa en " + FARO + " para 4 personas").intent();

        assertThat(intent.missingContext()).containsExactly("requestedAt");
    }

    // ---- continuación de una intención pendiente ----

    private List<Message> historyWithPendingReservation() {
        Conversation conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        conversation.addExplorerMessage(MessageContent.of("quiero reservar una mesa en " + FARO));
        conversation.addLiaReply(MessageContent.of("¿Para qué fecha y hora?"),
                new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, null, null, HuariqueId.of(FARO)));
        return conversation.getMessages();
    }

    @Test
    void laRespuestaSinPalabrasClaveContinuaLaReservaPendiente() {
        UserIntent intent = interpreter.interpret(
                "mañana a las 8 pm para 4 personas", MIRAFLORES, historyWithPendingReservation()).intent();

        assertThat(intent.type()).isEqualTo(IntentType.RESERVATION_REQUEST);
        assertThat(intent.huariqueId()).as("viene de la intención pendiente").isEqualTo(HuariqueId.of(FARO));
        assertThat(intent.partySize()).isEqualTo(4);
        assertThat(intent.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 20, 0));
        assertThat(intent.isComplete()).isTrue();
    }

    @Test
    void laContinuacionConservaLosDatosPreviosQueElNuevoMensajeNoTrae() {
        Conversation conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        conversation.addLiaReply(MessageContent.of("¿Para qué fecha y hora?"), new UserIntent(
                IntentType.RESERVATION_REQUEST, null, null, null, 4, null, HuariqueId.of(FARO)));

        UserIntent intent = interpreter.interpret("mañana 20:00", MIRAFLORES, conversation.getMessages()).intent();

        assertThat(intent.partySize()).isEqualTo(4);
        assertThat(intent.isComplete()).isTrue();
    }

    @Test
    void unaPalabraClaveDeOtroTipoNoMezclaLaIntencionPendiente() {
        UserIntent intent = interpreter.interpret(
                "mejor dime el menú", MIRAFLORES, historyWithPendingReservation()).intent();

        assertThat(intent.type()).isEqualTo(IntentType.HUARIQUE_INQUIRY);
        assertThat(intent.partySize()).isNull();
        // El huarique sí se recuerda: el explorador lo mencionó antes en la conversación.
        assertThat(intent.huariqueId()).isEqualTo(HuariqueId.of(FARO));
    }

    @Test
    void unaIntencionYaCompletaNoSeContinua() {
        Conversation conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        conversation.addLiaReply(MessageContent.of("Listo"), new UserIntent(
                IntentType.RESERVATION_REQUEST, null, null, null, 4, LocalDateTime.of(2026, 11, 14, 20, 0),
                HuariqueId.of(FARO)));

        UserIntent intent = interpreter.interpret("gracias", MIRAFLORES, conversation.getMessages()).intent();

        assertThat(intent.type()).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    void elHuariqueSeRecuerdaDeLosMensajesAnterioresDelExplorador() {
        Conversation conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        conversation.addExplorerMessage(MessageContent.of("me interesa " + ANTICUCHOS));
        conversation.addLiaReply(MessageContent.of("Dime más"), null);

        UserIntent intent = interpreter.interpret("cuál es el horario", MIRAFLORES, conversation.getMessages()).intent();

        assertThat(intent.huariqueId()).isEqualTo(HuariqueId.of(ANTICUCHOS));
    }
}
