package com.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    @DisplayName("Should encode positive numbers to 7-character strings")
    void shouldEncodeToSevenCharacters() {
        // Given
        long id = 123456789L;

        // When
        String encoded = encoder.encode(id);

        // Then
        assertThat(encoded).hasSize(7);
        assertThat(encoded).matches("[0-9A-Za-z]+");
    }

    @Test
    @DisplayName("Should decode encoded string back to original number")
    void shouldDecodeBackToOriginal() {
        // Given
        long original = 987654321L;
        String encoded = encoder.encode(original);

        // When
        long decoded = encoder.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 100L, 10000L, 1000000L, 100000000L, Long.MAX_VALUE / 2 })
    @DisplayName("Should encode and decode various numbers correctly")
    void shouldEncodeAndDecodeVariousNumbers(long number) {
        // When
        String encoded = encoder.encode(number);
        long decoded = encoder.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(number);
    }

    @Test
    @DisplayName("Should generate unique codes for different IDs")
    void shouldGenerateUniqueCodes() {
        // Given
        Set<String> codes = new HashSet<>();
        int count = 10000;

        // When
        for (long i = 1; i <= count; i++) {
            codes.add(encoder.encode(i));
        }

        // Then
        assertThat(codes).hasSize(count);
    }

    @Test
    @DisplayName("Should encode zero correctly")
    void shouldEncodeZero() {
        // When
        String encoded = encoder.encode(0L);

        // Then
        assertThat(encoded).isNotNull();
        assertThat(encoded).hasSize(7);
    }

    @Test
    @DisplayName("Should only use alphanumeric characters")
    void shouldOnlyUseAlphanumericCharacters() {
        // Given
        long[] testValues = { 1L, 100L, 12345L, 999999999L };

        for (long value : testValues) {
            // When
            String encoded = encoder.encode(value);

            // Then
            assertThat(encoded).matches("^[0-9A-Za-z]+$");
        }
    }
}
