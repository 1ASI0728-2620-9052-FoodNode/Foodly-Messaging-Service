package pe.foodly.messaging.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.enums.RoutingDecision;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.PriceRange;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

class IntentRoutingServiceTest {

    private static final GeoCoordinates LOCATION = new GeoCoordinates(-12.12, -77.03);
    private static final HuariqueId HUARIQUE = HuariqueId.of("22222222-2222-2222-2222-222222222222");
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 20, 20, 0);

    private final IntentRoutingService service = new IntentRoutingService();

    @Test
    void recomendacionConUbicacionVaARadar() {
        UserIntent intent = new UserIntent(IntentType.RECOMMENDATION_REQUEST, "ceviche",
                new PriceRange(BigDecimal.ZERO, new BigDecimal("25")), LOCATION, null, null, null);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.RECOMMEND_VIA_RADAR);
    }

    @Test
    void recomendacionSinUbicacionPideAclaracion() {
        UserIntent intent = new UserIntent(IntentType.RECOMMENDATION_REQUEST, "ceviche",
                null, null, null, null, null);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.ASK_CLARIFICATION);
        assertThat(intent.missingContext()).containsExactly("location");
    }

    @Test
    void consultaDeHuariqueConHuariqueVaABusiness() {
        UserIntent intent = new UserIntent(IntentType.HUARIQUE_INQUIRY, null, null, null, null, null, HUARIQUE);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.QUERY_BUSINESS);
    }

    @Test
    void consultaDeHuariqueSinHuariquePideAclaracion() {
        UserIntent intent = new UserIntent(IntentType.HUARIQUE_INQUIRY, null, null, null, null, null, null);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.ASK_CLARIFICATION);
        assertThat(intent.missingContext()).containsExactly("huariqueId");
    }

    @Test
    void reservaCompletaSeReenviaAReservation() {
        UserIntent intent = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 4, WHEN, HUARIQUE);

        assertThat(intent.isComplete()).isTrue();
        assertThat(intent.missingContext()).isEmpty();
        assertThat(service.route(intent)).isEqualTo(RoutingDecision.FORWARD_TO_RESERVATION);
    }

    @Test
    void reservaSinFechaPideAclaracionYFaltaRequestedAt() {
        UserIntent intent = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 4, null, HUARIQUE);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.ASK_CLARIFICATION);
        assertThat(intent.isComplete()).isFalse();
        assertThat(intent.missingContext()).containsExactly("requestedAt");
    }

    @Test
    void reservaSinPersonasNiHuariquePideAclaracionConTodoLoQueFalta() {
        UserIntent intent = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, null, WHEN, null);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.ASK_CLARIFICATION);
        assertThat(intent.missingContext()).containsExactly("huariqueId", "partySize");
    }

    @Test
    void laReservaNoValidaHorarioNiCapacidad() {
        // Messaging no valida horario ni capacidad: una fecha pasada o un grupo enorme igual se reenvía.
        UserIntent intent = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 500,
                LocalDateTime.of(2000, 1, 1, 3, 0), HUARIQUE);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.FORWARD_TO_RESERVATION);
    }

    @Test
    void intencionDesconocidaPideAclaracion() {
        UserIntent intent = new UserIntent(IntentType.UNKNOWN, null, null, LOCATION, 2, WHEN, HUARIQUE);

        assertThat(service.route(intent)).isEqualTo(RoutingDecision.ASK_CLARIFICATION);
        assertThat(intent.isComplete()).isFalse();
        assertThat(intent.missingContext()).containsExactly("type");
    }

    @Test
    void unaIntencionNulaLanzaExcepcion() {
        assertThatThrownBy(() -> service.route(null)).isInstanceOf(NullPointerException.class);
    }
}
