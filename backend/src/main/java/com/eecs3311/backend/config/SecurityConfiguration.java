package com.eecs3311.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        // CSRF remains enabled; GUI and CLI fetch a session token before creating courses.
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .build();
    }

    @Bean
    UserDetailsService users(@Value("${study.auth.username}") String username,
                             @Value("${study.auth.password}") String password) {
        if (username.isBlank() || password.isBlank() || password.equals("REPLACE_WITH_YOUR_LOCAL_PASSWORD")) {
            throw new IllegalStateException("Set STUDY_PASSWORD or study.auth.password in ignored config.local.properties before starting the backend.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("STUDENT").build());
    }
}
