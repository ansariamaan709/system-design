package com.urlshortener.exception;

public class AliasAlreadyExistsException extends RuntimeException {
    
    public AliasAlreadyExistsException(String message) {
        super(message);
    }
    
    public AliasAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
