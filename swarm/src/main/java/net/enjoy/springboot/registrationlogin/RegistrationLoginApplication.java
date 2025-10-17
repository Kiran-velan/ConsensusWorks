package net.enjoy.springboot.registrationlogin;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RegistrationLoginApplication {

    public static void main(String[] args) {
        // Only load .env for local development
        if (isLocalDevelopment()) {
            Dotenv dotenv = Dotenv.configure()
                    .directory("..")
                    .ignoreIfMissing()
                    .filename(".env")
                    .load();
            dotenv.entries().forEach(entry ->
                    System.setProperty(entry.getKey(), entry.getValue())
            );
        }

        SpringApplication.run(RegistrationLoginApplication.class, args);
    }

    private static boolean isLocalDevelopment() {
        return System.getenv("RAILWAY") == null &&
                System.getenv("RAILWAY_ENVIRONMENT") == null;
    }
}