package com.mosbyboys.dishesbecrazy.technical.security;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message){
        super(message);
    }    
}
