package com.gymora.membership.application;

import com.gymora.common.error.BusinessRuleException;
import org.springframework.http.HttpStatus;

/**
 * Phone numbers are stored in one shape so "98 123 456", "98-123-456" and "98123456" are the same
 * member: digits only, with a leading + kept for international numbers ("00216…" becomes "+216…").
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** Normalized number, or a 400 error.member_phone_invalid when it can't be a phone number. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw invalid(raw);
        }
        String trimmed = raw.trim();
        boolean international = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (!international && digits.startsWith("00")) {
            international = true;
            digits = digits.substring(2);
        }
        if (digits.length() < 6 || digits.length() > 15) {
            throw invalid(raw);
        }
        return international ? "+" + digits : digits;
    }

    /** Same as {@link #normalize} for optional fields: blank stays null. */
    public static String normalizeOptional(String raw) {
        return raw == null || raw.isBlank() ? null : normalize(raw);
    }

    /** The digits a search query contains, for matching phone numbers; empty when fewer than 3. */
    public static String searchDigits(String query) {
        String digits = query == null ? "" : query.replaceAll("\\D", "");
        return digits.length() >= 3 ? digits : "";
    }

    private static BusinessRuleException invalid(String raw) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, "error.member_phone_invalid", raw == null ? "" : raw);
    }
}
