package pe.foodly.messaging.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;

class MessageContentTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void unMensajeVacioOEnBlancoLanzaExcepcion(String text) {
        assertThatThrownBy(() -> MessageContent.of(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void unMensajeMasLargoQueElMaximoPorDefectoLanzaExcepcion() {
        String tooLong = "a".repeat(MessageContent.DEFAULT_MAX_LENGTH + 1);

        assertThatThrownBy(() -> MessageContent.of(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void unMensajeConLaLongitudMaximaExactaEsValido() {
        MessageContent content = MessageContent.of("a".repeat(1000));

        assertThat(content.length()).isEqualTo(1000);
        assertThat(content.isBlank()).isFalse();
    }

    @Test
    void elMaximoEsConfigurable() {
        assertThat(MessageContent.of("hola", 4).text()).isEqualTo("hola");
        assertThatThrownBy(() -> MessageContent.of("hola!", 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4");
        assertThat(MessageContent.of("a".repeat(2000), 2000).length()).isEqualTo(2000);
    }

    @Test
    void unMaximoMenorQueUnoLanzaExcepcion() {
        assertThatThrownBy(() -> MessageContent.of("hola", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosContenidosConElMismoTextoSonIguales() {
        assertThat(MessageContent.of("hola")).isEqualTo(MessageContent.of("hola"));
        assertThat(MessageContent.of("hola")).hasSameHashCodeAs(MessageContent.of("hola"));
    }
}
