package com.urlshortener.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            // If URL doesn't have a protocol, add https://
            String urlToValidate = value;
            if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                urlToValidate = "https://" + value;
            }

            // Try to parse as URL
            URL url = new URL(urlToValidate);

            // Validate protocol
            String protocol = url.getProtocol().toLowerCase();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL must use HTTP or HTTPS protocol")
                        .addConstraintViolation();
                return false;
            }

            // Validate host is not empty
            if (url.getHost() == null || url.getHost().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL must have a valid host")
                        .addConstraintViolation();
                return false;
            }

            return true;
        } catch (MalformedURLException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid URL format: " + e.getMessage())
                    .addConstraintViolation();
            return false;
        }
    }
}
