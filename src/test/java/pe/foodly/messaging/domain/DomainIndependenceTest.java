package pe.foodly.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** El dominio no importa JPA, Spring ni HTTP (ni nada de las otras capas). */
class DomainIndependenceTest {

    private static final Path DOMAIN = Path.of("src/main/java/pe/foodly/messaging/domain");
    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^import\\s+(static\\s+)?(org\\.springframework|jakarta|javax|java\\.net\\.http|com\\.fasterxml"
                    + "|org\\.hibernate|pe\\.foodly\\.messaging\\.(application|interfaces|infrastructure))\\b.*",
            Pattern.MULTILINE);

    @Test
    void elDominioSoloDependeDeJava() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(DOMAIN)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(sources).isNotEmpty();

        for (Path source : sources) {
            assertThat(FORBIDDEN_IMPORT.matcher(Files.readString(source)).find())
                    .as("%s importa una dependencia prohibida", source)
                    .isFalse();
        }
    }
}
