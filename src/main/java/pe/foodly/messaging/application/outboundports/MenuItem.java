package pe.foodly.messaging.application.outboundports;

import java.math.BigDecimal;

/** Un plato del menú de un huarique, con su precio en soles ({@code price} puede ser {@code null}). */
public record MenuItem(String name, String description, BigDecimal price) {
}
