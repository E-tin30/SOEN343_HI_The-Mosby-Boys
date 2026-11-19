package com.mosbyboys.dishesbecrazy.technical.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseImageStorage {

    private static final String BUCKET = "menu-images";

    private final String supabaseUrl;
    private final String secretKey;
    private final RestClient restClient = RestClient.create();

    public SupabaseImageStorage(
            @Value("${spring.supabase.url}") String supabaseUrl,
            @Value("${spring.supabase.secret-key}") String secretKey
    ) {
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.secretKey = secretKey;
    }

    public String upload(UUID itemId, byte[] image, String contentType) {

        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Image cannot be empty.");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException(
                    "Unsupported image format."
            );
        };

        String path = itemId + "." + extension;

        //Upload image to Supabase Storage
        restClient.post()
                .uri(supabaseUrl + "/storage/v1/object/"
                        + BUCKET + "/" + path)
                .header("Authorization", "Bearer " + secretKey)
                .header("apikey", secretKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .body(image)
                .retrieve()
                .toBodilessEntity();

        //Return public image URL
        return supabaseUrl
                + "/storage/v1/object/public/"
                + BUCKET + "/" + path;
    }

    public void delete(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        URI uri = URI.create(imageUrl);

        String expectedPrefix = "/storage/v1/object/public/"
                + BUCKET + "/";

        String objectPath = uri.getRawPath();

        if (!uri.getHost().equals(URI.create(supabaseUrl).getHost())
                || !objectPath.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Invalid Supabase image URL."
            );
        }

        String filePath = objectPath.substring(expectedPrefix.length());

        //Delete image from Supabase Storage
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri(supabaseUrl + "/storage/v1/object/" + BUCKET)
                .header("Authorization", "Bearer " + secretKey)
                .header("apikey", secretKey)
                .body(Map.of("prefixes", List.of(filePath)))
                .retrieve()
                .toBodilessEntity();
    }
}
