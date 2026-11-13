package pe.foodly.messaging.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Habilita las tareas programadas ({@code LiaTimeoutJob}) con su propio scheduler. Sin esto, {@code @Scheduled}
 * adopta el único {@code TaskScheduler} del contexto, que es el del broker STOMP: una consulta lenta del job
 * retrasaría los latidos de los clientes conectados.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
