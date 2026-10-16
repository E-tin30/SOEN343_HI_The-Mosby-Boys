package com.mosbyboys.dishesbecrazy.technical.repository;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

@Repository
public class AccountRepo{

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

	public AccountRepo(JdbcTemplate jdbcTemplate, @Value("${spring.supabase.url}") String url, @Value("${spring.supabase.publishable-key}") String key) {
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = RestClient.builder().baseUrl(url).defaultHeader("apikey", key).build();
    }

    public UUID createAuthUser(String displayName, String email, String password) {//Sends an API request to Supabase to create an entry in auth.users
        Map<?, ?> response = restClient.post().uri("/auth/v1/signup").contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email,"password", password, "data", Map.of("display_name", displayName))).retrieve().body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty response from Supabase");
        }

        Object user = response.get("user");

        if (!(user instanceof Map<?, ?>)) {
            throw new IllegalStateException("Invalid Supabase response");
        }

        Map<?, ?> userMap = (Map<?, ?>) user;

        Object id = userMap.get("id");

        if (!(id instanceof String)) {
            throw new IllegalStateException("User ID missing");
        }

        String userId = (String) id;

        return UUID.fromString(userId);
    }

    public void saveUserAccount(UUID id, String firstName, String lastName, String username, String role) {//Writes to our custom public.User table
        String sql = "INSERT INTO public.\"Users\" (id, first_name, last_name, username, role) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, firstName, lastName, username, role);
    }
}
