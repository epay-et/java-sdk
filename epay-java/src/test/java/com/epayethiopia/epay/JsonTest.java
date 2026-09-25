package com.epayethiopia.epay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.epayethiopia.epay.internal.Json;

class JsonTest {

    @Test
    void parsesEveryValueType() {
        Object parsed = Json.parse(" {\"s\":\"x\",\"n\":-12.50e1,\"t\":true,\"f\":false,\"z\":null,"
                + "\"a\":[1,[],{}],\"o\":{\"k\":\"v\"}} ");

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertThat(map.get("s")).isEqualTo("x");
        assertThat((BigDecimal) map.get("n")).isEqualByComparingTo("-125");
        assertThat(map.get("t")).isEqualTo(true);
        assertThat(map.get("f")).isEqualTo(false);
        assertThat(map.containsKey("z")).isTrue();
        assertThat(map.get("z")).isNull();
        assertThat((List<?>) map.get("a")).hasSize(3);
        assertThat(((Map<?, ?>) map.get("o")).get("k")).isEqualTo("v");
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsKeyOrderAndReturnsUnmodifiableCollections() {
        Map<Object, Object> map = (Map<Object, Object>) Json.parse("{\"b\":1,\"a\":2,\"c\":3}");
        assertThat(map.keySet()).containsExactly("b", "a", "c");
        assertThatThrownBy(() -> map.put("d", 4))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void numbersAreExact() {
        assertThat(Json.parse("0.1")).isEqualTo(new BigDecimal("0.1"));
        assertThat(Json.parse("12345678901234567890.123456789"))
                .isEqualTo(new BigDecimal("12345678901234567890.123456789"));
    }

    @Test
    void decodesEscapesAndSurrogatePairs() {
        assertThat(Json.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\\ud83d\\ude00\""))
                .isEqualTo("a\"b\\c/d\b\f\n\r\té😀");
        assertThat(Json.parse("\"ብር\"")).isEqualTo("ብር");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "   ", "{", "[1,]", "{\"a\":1,}", "01", "1.", ".5", "-", "1e", "+1", "tru", "nul",
        "\"unterminated", "\"bad \\x escape\"", "\"\\u12\"", "{\"a\" 1}", "{a:1}", "[1 2]",
        "1 2", "\"tab\tinside\"", "NaN", "1e99999999999"
    })
    void rejectsInvalidDocuments(String input) {
        assertThatThrownBy(() -> Json.parse(input)).isInstanceOf(Json.JsonException.class);
    }

    @Test
    void rejectsHostileNesting() {
        String deep = "[".repeat(10_000) + "]".repeat(10_000);
        assertThatThrownBy(() -> Json.parse(deep))
                .isInstanceOf(Json.JsonException.class)
                .hasMessageContaining("nesting");

        String allowed = "[".repeat(512) + "]".repeat(512);
        assertThat(Json.parse(allowed)).isInstanceOf(List.class);
    }

    @Test
    void writesAndRoundTrips() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("amount", "250.00");
        value.put("n", 42);
        value.put("d", new BigDecimal("1.50"));
        value.put("ok", true);
        value.put("none", null);
        value.put("list", Arrays.asList("a", 1, null));
        value.put("text", "quote\" back\\ nl\n ctl\u0001 ls\u2028 é");

        String written = Json.write(value);

        assertThat(written).startsWith("{\"amount\":\"250.00\",\"n\":42,\"d\":1.50,\"ok\":true,\"none\":null,");
        assertThat(written).contains("\\u0001").contains("\\u2028").contains("\\n").contains("\\\"");
        assertThat(Json.parse(written)).isEqualTo(Json.parse(written));
        assertThat(((Map<?, ?>) Json.parse(written)).get("text")).isEqualTo(value.get("text"));
    }

    @Test
    void refusesUnserializableValues() {
        assertThatThrownBy(() -> Json.write(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.write(new Object())).isInstanceOf(IllegalArgumentException.class);
    }
}
