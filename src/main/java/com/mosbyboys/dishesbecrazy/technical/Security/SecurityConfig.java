package com.mosbyboys.dishesbecrazy.technical.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("security-disabled")

public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> {})
            .build();
    }
}