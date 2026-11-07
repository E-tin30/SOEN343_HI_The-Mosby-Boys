package com.mosbyboys.dishesbecrazy.technical.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import com.mosbyboys.dishesbecrazy.technical.security.InvalidCredentialsException;

@Repository
public class UserRepo{

    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    public record LoginTokens(String accessToken, String refreshToken, long expiresIn){

    }

    public record UserProfile(UUID id, String firstName, String lastName, String username, String role) {

    }

    private static final RowMapper<UserProfile> PROFILE = (rs, rowNum) -> new UserProfile(rs.getObject("id", UUID.class),
        rs.getString("first_name"), rs.getString("last_name"), rs.getString("username"), rs.getString("role"));

	public UserRepo(JdbcTemplate jdbcTemplate, @Value("${spring.supabase.url}") String url, @Value("${spring.supabase.publishable-key}") String key) {
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

    public void saveUser(UUID id, String firstName, String lastName, String username, String role, String address, String restaurantName, Location location) {
        jdbcTemplate.update(
            "INSERT INTO public.\"User\" (id, first_name, last_name, username, role) VALUES (?, ?, ?, ?, ?)",
            id, firstName, lastName, username, role);

        Double lat = location == null ? null : location.latitude();
        Double lon = location == null ? null : location.longitude();

        switch (role) {
            case "CUSTOMER" -> jdbcTemplate.update(
                "INSERT INTO public.\"Customer\" (user_id, created_at, address, latitude, longitude) "
            + "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)",
                id, address, lat, lon);
            case "RESTAURANT" -> jdbcTemplate.update(
                "INSERT INTO public.\"Restaurant\" (user_id, created_at, name, status, latitude, longitude) "
            + "VALUES (?, CURRENT_TIMESTAMP, ?, 'OPEN', ?, ?)",
                id, restaurantName, lat, lon);
            case "COURIER" -> jdbcTemplate.update(
                "INSERT INTO public.\"Courier\" (user_id, created_at, state, latitude, longitude) "
            + "VALUES (?, CURRENT_TIMESTAMP, 'OFFLINE', ?, ?)",
                id, lat, lon);
            default -> { }
        }
    }

    public LoginTokens login(String email, String password){
        try{
            Map<?, ?> response = restClient.post().uri("/auth/v1/token?grant_type=password").contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email, "password", password)).retrieve().body(Map.class);
    
            if (response == null){
                throw new IllegalStateException("Empty response from Supabase");
            }

            Object accessToken = response.get("access_token");
            Object refreshToken = response.get("refresh_token");
            Object expiresIn = response.get("expires_in");

            if (!(accessToken instanceof String)||!(refreshToken instanceof String)||!(expiresIn instanceof Number)){
                throw new IllegalStateException ("Invalid login response");
            }

            return new LoginTokens((String) accessToken, (String) refreshToken, ((Number) expiresIn).longValue()); 
        } catch (HttpClientErrorException.BadRequest e){
            throw new InvalidCredentialsException("Invalid email or password");
        }

    }

    public Optional<UserProfile> findUserById(UUID id) {
        String sql = "SELECT id, first_name, last_name, username, role FROM public.\"User\" WHERE id = ?";

        return jdbcTemplate.query(sql, PROFILE, id).stream().findFirst();
    }

    // Loads many users in one query, keyed by id.
    public Map<UUID, UserProfile> findUsersByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT id, first_name, last_name, username, role FROM public.\"User\" WHERE id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql, PROFILE, ids.toArray()).stream().collect(Collectors.toMap(UserProfile::id, p -> p));
    }
}
