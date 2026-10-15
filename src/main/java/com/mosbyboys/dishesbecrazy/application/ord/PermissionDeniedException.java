package com.mosbyboys.dishesbecrazy.application.ord;

public class PermissionDeniedException extends RuntimeException {
    
    public PermissionDeniedException(String message){
        super(message);
    }
}
