package pe.foodly.messaging.infrastructure.clients;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/** Huariques de ejemplo de Lima (nombres ficticios, coordenadas aproximadas) para el perfil {@code stub}. */
final class StubLimaData {

    record Entry(HuariqueDetails details, GeoCoordinates location, List<MenuItem> menu) {
    }

    static final List<Entry> HUARIQUES = List.of(
            entry("a1111111-1111-4111-8111-111111111111", "Cevichería El Faro Miraflorino",
                    "Cevichería de pescado fresco del día.", "Av. Larco 345, Miraflores", "12:00 - 17:00", true,
                    -12.1219, -77.0297,
                    new MenuItem("Ceviche clásico", "Pescado del día con leche de tigre", new BigDecimal("32.00")),
                    new MenuItem("Jalea mixta", "Pescados y mariscos fritos", new BigDecimal("45.00")),
                    new MenuItem("Chicha morada", null, new BigDecimal("8.00"))),
            entry("a2222222-2222-4222-8222-222222222222", "Anticuchos Doña Rosa",
                    "Anticuchos de corazón a la parrilla.", "Jr. Bolognesi 120, Barranco", "18:00 - 23:00", true,
                    -12.1497, -77.0219,
                    new MenuItem("Anticuchos de corazón", "Con papa dorada y choclo", new BigDecimal("22.00")),
                    new MenuItem("Mazamorra morada", null, new BigDecimal("9.00"))),
            entry("a3333333-3333-4333-8333-333333333333", "Picantería La Chalaca Surquillana",
                    "Comida criolla casera de menú.", "Av. Angamos Este 810, Surquillo", "11:00 - 16:00", false,
                    -12.1118, -77.0114,
                    new MenuItem("Ají de gallina", "Con arroz blanco y papa", new BigDecimal("18.00")),
                    new MenuItem("Lomo saltado", null, new BigDecimal("24.00")),
                    new MenuItem("Arroz con pollo", null, new BigDecimal("17.00"))),
            entry("a4444444-4444-4444-8444-444444444444", "Sanguchería Lince Criollo",
                    "Sánguches de pan francés y jugos de fruta.", "Av. Arenales 1530, Lince", "08:00 - 21:00", true,
                    -12.0856, -77.0336,
                    new MenuItem("Pan con chicharrón", "Con camote frito y salsa criolla", new BigDecimal("14.00")),
                    new MenuItem("Jugo surtido", null, new BigDecimal("10.00"))));

    private StubLimaData() {
    }

    static Optional<Entry> find(HuariqueId id) {
        return HUARIQUES.stream().filter(entry -> entry.details().id().equals(id)).findFirst();
    }

    private static Entry entry(
            String id, String name, String description, String address, String hours, boolean open,
            double latitude, double longitude, MenuItem... menu) {
        return new Entry(
                new HuariqueDetails(HuariqueId.of(id), name, description, address, hours, open),
                new GeoCoordinates(latitude, longitude),
                List.of(menu));
    }
}
