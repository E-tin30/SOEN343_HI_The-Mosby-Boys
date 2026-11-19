package com.mosbyboys.dishesbecrazy.technical.storage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
public class SupabaseImageStorage {

    //TODO: Supabase configuration and upload implementation

    public String upload(UUID itemId, byte[] image, String contentType) {

        //TODO: Upload image to Supabase Storage

        return null; //imageUrl;
    }

    public void delete(String imageUrl) {

        //TODO: Delete image from Supabase Storage
    }
}
