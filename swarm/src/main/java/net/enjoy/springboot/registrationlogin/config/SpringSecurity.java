package net.enjoy.springboot.registrationlogin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class SpringSecurity {

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                "/ws/**",                // WebSocket connections
                                "/discussions/discussion/exit"
                        )
                        // ✅ Disable CSRF for REST endpoints and WS (safe with JWT/session setup)
                        .disable()
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/home", "/register/**", "/user", "/organizer", "/discussion",
                                "/discussions", "/discussions/**", "/discussions/validate", "/favicon.ico",
                                "/error", "/session", "/session/**", "/session/discussion-details",
                                "/send-otp", "/verify-otp", "/ws/**", "/images/**", "/actuator/**",
                                "/topic/**", "/app/**", "/userRegister/**", "/discussions/discussion/exit",
                                "/send-feedback", "/api/export/**"
                        ).permitAll()
                        .requestMatchers("/organizer-dashboard", "/past").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect("/?sessionExpired")
                        )
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/organizer-dashboard", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/home?logout")
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .sessionFixation().newSession()
                        .maximumSessions(1)
                        .expiredUrl("/login?expired")
                )
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }

    @Bean
    public AuthenticationConfiguration authenticationConfiguration() {
        return new AuthenticationConfiguration();
    }
}
