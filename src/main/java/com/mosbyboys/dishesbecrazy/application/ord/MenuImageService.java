package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.technical.storage.SupabaseImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuImageService {

    private final SupabaseImageStorage supabaseImageStorage;

    //saves a menu item image to storage
    @Transactional
    public String save(UUID itemId, byte[] image, String contentType) {
        return supabaseImageStorage.upload(itemId, image, contentType);
    }
}
