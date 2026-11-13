package pe.foodly.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada del Messaging Service (MS). */
@SpringBootApplication
public class MessagingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingServiceApplication.class, args);
    }
}
