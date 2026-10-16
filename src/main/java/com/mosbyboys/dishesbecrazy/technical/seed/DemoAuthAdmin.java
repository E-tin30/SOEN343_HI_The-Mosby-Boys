package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Creates demo logins through Supabase Auth.
@Component
@Profile("demo-seed")
public class DemoAuthAdmin {
    private final String url;
    private final String key;
    private final String password;

    // Reads the URL, admin key, and demo password from the environment.
    public DemoAuthAdmin(@Value("${SUPABASE_URL:}") String url,
                         @Value("${SUPABASE_SERVICE_ROLE_KEY:${SUPABASE_SECRET_KEY:}}") String key,
                         @Value("${DEMO_SEED_PASSWORD:}") String password) {
        this.url = url;
        this.key = key;
        this.password = password;
    }

    public UUID create(DemoProfileSeeder.Account account) {
        // Rejects missing configuration before sending a request.
        if (!url.startsWith("https://") || key.isBlank() || password.length() < 8) {
            throw new IllegalStateException("Set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SECRET_KEY), "
                    + "and DEMO_SEED_PASSWORD (at least 8 characters) before seeding Auth accounts.");
        }
        try {
            // Builds the admin request with JSON data.
            var request = RestClient.create(url).post().uri("/auth/v1/admin/users")
                    .header("apikey", key)
                    .contentType(MediaType.APPLICATION_JSON);
            // Legacy keys also need a Bearer header.
            if (!key.startsWith("sb_secret_")) {
                request.header("Authorization", "Bearer " + key);
            }
            // Confirms the email and sends identity fields plus the role.
            var response = request.body(Map.of(
                    "email", account.email(), "password", password, "email_confirm", true,
                    "user_metadata", Map.of("first_name", account.firstName(),
                            "last_name", account.lastName(), "username", account.username()),
                    "app_metadata", Map.of("role", account.role().name())))
                    .retrieve().body(AuthUser.class);
            if (response == null || response.id() == null) {
                throw new IllegalStateException("Supabase did not return a demo Auth user ID.");
            }
            // Links the Auth account to its public profile.
            return response.id();
        } catch (RestClientException exception) {
            // Keeps credentials out of error messages.
            throw new IllegalStateException("Supabase Admin API could not create " + account.email()
                    + ". Check the project URL, admin key, and password policy.");
        }
    }

    // Reads only the ID from the API response.
    public record AuthUser(UUID id) {
    }
}
