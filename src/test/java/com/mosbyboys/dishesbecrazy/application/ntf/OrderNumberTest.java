package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderNumberTest {

    @Test
    void usesTheLastSixCharactersOfTheId() {
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456abcdef");

        assertEquals("#abcdef", OrderNumber.of(id));
    }
}