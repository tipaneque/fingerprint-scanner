package com.github.bluestring_digital.fingerprint.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CaptureValidator {
    private static final int MIN_WIDTH = 100;
    private static final int MAX_WIDTH = 4000;
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 4000;
    private static final int MIN_QUALITY = 0;
    private static final int MAX_QUALITY = 100;

    /**
     * Validates image dimensions.
     */
    public void validateDimensions(int width, int height) {
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new IllegalArgumentException(
                    "Invalid width: " + width + " (must be between " + MIN_WIDTH + " e " + MAX_WIDTH + ")"
            );
        }
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
            throw new IllegalArgumentException(
                    "Invalid height: " + height + " (must be between " + MIN_HEIGHT + " e " + MAX_HEIGHT + ")"
            );
        }
    }

    /**
     * Validates image buffer
     */
    public void validateImageBuffer(byte[] imageData, int expectedSize) {
        if (imageData == null) {
            throw new IllegalArgumentException("Image buffer is null");
        }
        if (imageData.length < expectedSize) {
            throw new IllegalArgumentException(
                    "Insufficient buffer: expected " + expectedSize + " bytes, received " + imageData.length
            );
        }
    }

    /**
     * Validates print quality.
     */
    public boolean isQualityAcceptable(int quality, int threshold) {
        if (quality < MIN_QUALITY || quality > MAX_QUALITY) {
            log.warn("Quality outside the valid range.: {}", quality);
            return false;
        }
        return quality >= threshold;
    }

    /**
     * Normalizes quality for the range [0, 100]
     */
    public int normalizeQuality(int quality) {
        if (quality < MIN_QUALITY) return MIN_QUALITY;
        if (quality > MAX_QUALITY) return MAX_QUALITY;
        return quality;
    }

    /**
     * Creates a safe image buffer with the correct size.
     */
    public byte[] createSafeBuffer(int width, int height) {
        validateDimensions(width, height);
        long size = (long) width * height;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Very large dimensions: " + width + "x" + height);
        }
        return new byte[(int) size];
    }

    /**
     * Checks if there is valid data in the buffer
     */
    public boolean hasValidData(byte[] buffer) {
        if (buffer == null || buffer.length == 0) {
            return false;
        }

        // Check if it's not a completely empty buffer (all 0s or all 255s).
        int zeros = 0;
        int maxValues = 0;
        int sampleSize = Math.min(1000, buffer.length);

        for (int i = 0; i < sampleSize; i++) {
            int value = buffer[i] & 0xFF;
            if (value == 0) zeros++;
            if (value == 255) maxValues++;
        }

        // If more than 95% are zeros or 255, it's probably invalid.
        double zeroRatio = (double) zeros / sampleSize;
        double maxRatio = (double) maxValues / sampleSize;

        return zeroRatio < 0.95 && maxRatio < 0.95;
    }
}
