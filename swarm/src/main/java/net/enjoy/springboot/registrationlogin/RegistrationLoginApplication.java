package net.enjoy.springboot.registrationlogin;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RegistrationLoginApplication {

    public static void main(String[] args) {
        // Load .env file from multiple possible locations
        Dotenv dotenv = Dotenv.configure()
                .directory("..") // Current directory
                .ignoreIfMissing()
                .filename(".env") // Explicitly specify filename
                .load();

        // Set system properties from .env
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(RegistrationLoginApplication.class, args);
    }
}