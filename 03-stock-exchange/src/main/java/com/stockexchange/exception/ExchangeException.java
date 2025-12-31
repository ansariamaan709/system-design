package com.stockexchange.exception;

import lombok.Getter;

/**
 * Custom exception for exchange errors.
 */
@Getter
public class ExchangeException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public ExchangeException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public ExchangeException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
