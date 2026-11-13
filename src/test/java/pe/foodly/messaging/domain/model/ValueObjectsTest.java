package pe.foodly.messaging.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.PriceRange;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

class ValueObjectsTest {

    @Test
    void priceRangeValidaLosExtremos() {
        PriceRange range = new PriceRange(BigDecimal.ZERO, new BigDecimal("25"));
        assertThat(range.min()).isEqualByComparingTo("0");
        assertThat(range.max()).isEqualByComparingTo("25");

        assertThat(new PriceRange(BigDecimal.TEN, BigDecimal.TEN).min()).isEqualByComparingTo("10");
        assertThatThrownBy(() -> new PriceRange(BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceRange(new BigDecimal("-1"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceRange(null, BigDecimal.ONE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceRange(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void geoCoordinatesValidaLosRangos() {
        assertThat(new GeoCoordinates(-90, 180).latitude()).isEqualTo(-90);
        assertThatThrownBy(() -> new GeoCoordinates(90.1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoCoordinates(-90.1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoCoordinates(0, 180.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoCoordinates(0, -180.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoCoordinates(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userIntentValidaTipoYCantidadDePersonas() {
        assertThatThrownBy(() -> new UserIntent(null, null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losIdentificadoresSonUuidYSeCreanDesdeTexto() {
        UUID uuid = UUID.randomUUID();
        assertThat(ExplorerId.of(uuid.toString()).value()).isEqualTo(uuid);
        assertThat(HuariqueId.of(uuid.toString())).isEqualTo(new HuariqueId(uuid));
        assertThat(ReservationId.of(uuid.toString()).toString()).isEqualTo(uuid.toString());
        assertThat(ConversationId.newId()).isNotEqualTo(ConversationId.newId());
        assertThat(MessageId.newId()).isNotEqualTo(MessageId.newId());

        assertThatThrownBy(() -> ExplorerId.of("no-es-un-uuid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExplorerId(null)).isInstanceOf(NullPointerException.class);
    }
}
