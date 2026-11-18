package com.gymora.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymora.common.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void sameNumberWrittenDifferentlyBecomesOne() {
        assertThat(PhoneNumbers.normalize("98 123 456")).isEqualTo("98123456");
        assertThat(PhoneNumbers.normalize("98-123-456")).isEqualTo("98123456");
        assertThat(PhoneNumbers.normalize(" (98) 123.456 ")).isEqualTo("98123456");
    }

    @Test
    void internationalNumbersKeepTheirPlus() {
        assertThat(PhoneNumbers.normalize("+216 98 123 456")).isEqualTo("+21698123456");
        assertThat(PhoneNumbers.normalize("00216 98 123 456")).isEqualTo("+21698123456");
    }

    @Test
    void tooShortOrTooLongIsRefused() {
        assertThatThrownBy(() -> PhoneNumbers.normalize("123")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize("1234567890123456")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize("not a phone")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void optionalPhoneStaysEmpty() {
        assertThat(PhoneNumbers.normalizeOptional("  ")).isNull();
        assertThat(PhoneNumbers.normalizeOptional(null)).isNull();
    }

    @Test
    void searchUsesDigitsOnlyFromThreeOn() {
        assertThat(PhoneNumbers.searchDigits("98 12")).isEqualTo("9812");
        assertThat(PhoneNumbers.searchDigits("Sami")).isEmpty();
        assertThat(PhoneNumbers.searchDigits("M-12")).isEmpty();
    }
}
