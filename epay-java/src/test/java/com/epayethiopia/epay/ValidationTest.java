package com.epayethiopia.epay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.epayethiopia.epay.exception.EpayValidationException;

class ValidationTest {

    @ParameterizedTest
    @CsvSource({
        "250, 250.00",
        "250.5, 250.50",
        "250.50, 250.50",
        "' 1.00 ', 1.00",
        "0.01, 0.01",
        "999999999.99, 999999999.99",
        "007, 007.00",
    })
    void formatsAmountStrings(String input, String expected) {
        assertThat(Validation.formatAmount(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "000.0", "-1", "1.234", "1000000000", "1,000", "abc", "", " ", "1e3", ".5", "5."})
    void rejectsBadAmountStrings(String input) {
        assertThatThrownBy(() -> Validation.formatAmount(input)).isInstanceOf(EpayValidationException.class);
    }

    @Test
    void formatsBigDecimals() {
        assertThat(Validation.formatAmount(new BigDecimal("250"))).isEqualTo("250.00");
        assertThat(Validation.formatAmount(new BigDecimal("250.500"))).isEqualTo("250.50");
        assertThat(Validation.formatAmount(new BigDecimal("1E+3"))).isEqualTo("1000.00");
        assertThat(Validation.formatAmount(Validation.MAX_AMOUNT)).isEqualTo("999999999.99");
    }

    @Test
    void rejectsBigDecimalsThatWouldNeedRounding() {
        assertThatThrownBy(() -> Validation.formatAmount(new BigDecimal("250.005")))
                .isInstanceOf(EpayValidationException.class)
                .hasMessageContaining("2 decimal places");
        assertThatThrownBy(() -> Validation.formatAmount(BigDecimal.ZERO)).isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.formatAmount(new BigDecimal("-1"))).isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.formatAmount(new BigDecimal("1000000000")))
                .isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.formatAmount((BigDecimal) null)).isInstanceOf(EpayValidationException.class);
    }

    @Test
    void convertsMinorUnitsBothWays() {
        assertThat(Validation.amountFromMinorUnits(25_000)).isEqualTo("250.00");
        assertThat(Validation.amountFromMinorUnits(1)).isEqualTo("0.01");
        assertThat(Validation.amountFromMinorUnits(99_999_999_999L)).isEqualTo("999999999.99");
        assertThatThrownBy(() -> Validation.amountFromMinorUnits(0)).isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.amountFromMinorUnits(100_000_000_000L))
                .isInstanceOf(EpayValidationException.class);

        assertThat(Validation.toMinorUnits("250")).isEqualTo(25_000);
        assertThat(Validation.toMinorUnits("0.5")).isEqualTo(50);
        assertThat(Validation.toMinorUnits("999999999.99")).isEqualTo(99_999_999_999L);
    }

    @ParameterizedTest
    @CsvSource({
        "+251911234567, +251911234567",
        "251911234567, +251911234567",
        "0911234567, +251911234567",
        "911234567, +251911234567",
        "0711234567, +251711234567",
        "'+251 91 123 4567', +251911234567",
        "'(091) 123-4567', +251911234567",
        "091.123.4567, +251911234567",
    })
    void normalizesPhones(String input, String expected) {
        assertThat(Validation.normalizePhone(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "+1 202 555 0100", "0811234567", "09112345678", "251811234567", "++251911234567"})
    void rejectsBadPhones(String input) {
        assertThatThrownBy(() -> Validation.normalizePhone(input)).isInstanceOf(EpayValidationException.class);
    }

    @Test
    void normalizesCurrencies() {
        assertThat(Validation.normalizeCurrency(" etb ")).isEqualTo("ETB");
        assertThatThrownBy(() -> Validation.normalizeCurrency("ET")).isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.normalizeCurrency("E1B")).isInstanceOf(EpayValidationException.class);
        assertThatThrownBy(() -> Validation.normalizeCurrency(null)).isInstanceOf(EpayValidationException.class);
    }

    @Test
    void masksSecrets() {
        assertThat(Validation.maskSecret("sk_test_abcdefghijklmnop")).isEqualTo("sk_test_…mnop");
        assertThat(Validation.maskSecret("short")).isEqualTo("***");
    }
}
