package pe.foodly.messaging.infrastructure.clients;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;

/** Construcción de los {@link RestClient} y traducción de sus errores a {@link ProviderUnavailableException}. */
final class ProviderCalls {

    private ProviderCalls() {
    }

    /** Cliente con timeout de conexión y de respuesta (por defecto 3 s, ver {@code ProviderClientsProperties}). */
    static RestClient.Builder restClientBuilder(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
    }

    static String bearer(ExplorerToken token) {
        return "Bearer " + token.value();
    }

    static <T> T execute(String provider, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            throw new ProviderUnavailableException(provider, "respondió " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new ProviderUnavailableException(provider, "no respondió a tiempo o no hay conexión", e);
        } catch (RestClientException e) {
            throw new ProviderUnavailableException(provider, "respuesta ilegible", e);
        }
    }

    /** Como {@link #execute}, pero un 404 significa «no existe» y devuelve vacío. */
    static <T> Optional<T> executeOptional(String provider, Supplier<T> call) {
        try {
            return Optional.ofNullable(execute(provider, call));
        } catch (ProviderUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
