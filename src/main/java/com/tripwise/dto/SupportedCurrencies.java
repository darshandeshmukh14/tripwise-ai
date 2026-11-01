package com.tripwise.dto;

import java.util.Locale;
import java.util.Set;

/**
 * Trip currencies supported by the AI service (must match {@code SUPPORTED_CURRENCIES} in ai-service/app/schemas.py).
 */
public final class SupportedCurrencies {

    public static final String DEFAULT = "INR";

    public static final Set<String> CODES = Set.of(
            "INR", "USD", "EUR", "GBP", "CHF", "AED", "OMR", "NPR", "BTN", "LKR", "MVR", "THB", "VND", "SGD", "MYR",
            "IDR", "JPY", "KRW", "KZT", "AZN", "GEL", "TRY", "EGP", "KES", "TZS", "ZAR", "MUR", "SCR", "MAD");

    private SupportedCurrencies() {
    }

    /**
     * @return the upper-cased code, or {@link #DEFAULT} when blank
     */
    public static String normalize(String currency) {
        return currency == null || currency.isBlank() ? DEFAULT : currency.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isSupported(String currency) {
        return CODES.contains(normalize(currency));
    }
}
