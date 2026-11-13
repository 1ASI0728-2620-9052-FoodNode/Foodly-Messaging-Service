package pe.foodly.messaging.infrastructure.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.domain.services.IntentRoutingService;

/** Beans que conectan el dominio y la aplicación (que no conocen a Spring) con la configuración. */
@Configuration
@EnableAsync
public class ApplicationBeansConfig {

    /** Zona horaria del servidor: la misma con la que el dominio guarda {@code sent_at} y {@code last_message_at}. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    IntentRoutingService intentRoutingService() {
        return new IntentRoutingService();
    }

    @Bean
    MessagingSettings messagingSettings(
            @Value("${messaging.max-length:1000}") int maxLength,
            @Value("${messaging.radar-k-ring:2}") int radarKRing) {
        return new MessagingSettings(maxLength, radarKRing);
    }

    /**
     * Procesa de forma asíncrona el ruteo del callback de Lía (consultas a Radar, Business y Reservation,
     * de hasta 3 s cada una), para que el Integration System reciba el 202 de inmediato.
     */
    @Bean(name = "liaCallbackExecutor")
    ThreadPoolTaskExecutor liaCallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lia-callback-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * Executor propio para pedir la interpretación a Lía después del commit. Si se llena la cola, la
     * solicitud se ejecuta en el hilo que la pidió en lugar de perderse.
     */
    @Bean(name = "liaTaskExecutor")
    ThreadPoolTaskExecutor liaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lia-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
