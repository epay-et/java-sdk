package com.epayethiopia.epay.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict RFC 8259 JSON reader and writer.
 *
 * <p>The SDK bundles its own codec so it has no runtime dependencies: pulling in
 * Jackson would tie every consumer to one Jackson major, and Spring Boot 3 and 4
 * ship different ones.
 *
 * <p>Values map to Java as follows: object to {@code Map<String, Object>} (insertion
 * ordered and unmodifiable), array to {@code List<Object>}, string to {@link String},
 * number to {@link BigDecimal} so no precision is lost, {@code true}/{@code false} to
 * {@link Boolean}, and {@code null} to {@code null}.
 *
 * <p>Not part of the public API.
 */
public final class Json {

    /** Deepest nesting accepted, so hostile input cannot overflow the stack. */
    static final int MAX_DEPTH = 512;

    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Parses a complete JSON document.
     *
     * @throws JsonException if the text is not exactly one valid JSON value
     */
    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected trailing content");
        }
        return value;
    }

    /**
     * Serializes a value built from maps, lists, strings, numbers, booleans, and nulls.
     *
     * @throws IllegalArgumentException for any other type, or a non-finite number
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    // ---------------------------------------------------------------- reading

    private Object readValue(int depth) {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || isDigit(c)) {
                    yield readNumber();
                }
                throw error("unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> readObject(int depth) {
        checkDepth(depth);
        pos++; // {
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return Collections.unmodifiableMap(map);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("expected a string key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue(depth));
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return Collections.unmodifiableMap(map);
            }
            if (c != ',') {
                pos--;
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray(int depth) {
        checkDepth(depth);
        pos++; // [
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return Collections.unmodifiableList(list);
        }
        while (true) {
            skipWhitespace();
            list.add(readValue(depth));
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return Collections.unmodifiableList(list);
            }
            if (c != ',') {
                pos--;
                throw error("expected ',' or ']'");
            }
        }
    }

    private String readString() {
        pos++; // opening quote
        StringBuilder out = null;
        int runStart = pos;
        while (true) {
            if (pos >= text.length()) {
                throw error("unterminated string");
            }
            char c = text.charAt(pos);
            if (c == '"') {
                String tail = text.substring(runStart, pos);
                pos++;
                return out == null ? tail : out.append(tail).toString();
            }
            if (c < 0x20) {
                throw error("unescaped control character in string");
            }
            if (c != '\\') {
                pos++;
                continue;
            }
            if (out == null) {
                out = new StringBuilder();
            }
            out.append(text, runStart, pos);
            pos++; // backslash
            char escape = next();
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                // Surrogate pairs arrive as two \\u escapes and are appended
                // one unit at a time, which reassembles them in the String.
                case 'u' -> out.append(readHexChar());
                default -> {
                    pos--;
                    throw error("invalid escape '\\" + escape + "'");
                }
            }
            runStart = pos;
        }
    }

    private char readHexChar() {
        if (pos + 4 > text.length()) {
            throw error("truncated \\u escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos + i), 16);
            if (digit < 0) {
                throw error("invalid \\u escape");
            }
            value = (value << 4) | digit;
        }
        pos += 4;
        return (char) value;
    }

    private BigDecimal readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            skipDigits();
        } else {
            throw error("invalid number");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) {
                throw error("expected a digit after the decimal point");
            }
            skipDigits();
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error("expected a digit in the exponent");
            }
            skipDigits();
        }
        try {
            return new BigDecimal(text.substring(start, pos));
        } catch (NumberFormatException e) {
            // An exponent beyond int range is valid JSON but not a BigDecimal.
            throw error("number out of range");
        }
    }

    private void skipDigits() {
        while (isDigit(peek())) {
            pos++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw error("invalid literal");
        }
        pos += literal.length();
        return value;
    }

    private void expect(char c) {
        if (next() != c) {
            pos--;
            throw error("expected '" + c + "'");
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos++);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private void checkDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("nesting deeper than " + MAX_DEPTH);
        }
    }

    private JsonException error(String message) {
        return new JsonException(message + " at position " + pos);
    }

    // ---------------------------------------------------------------- writing

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(out, string);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof BigDecimal decimal) {
            out.append(decimal.toPlainString());
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON cannot represent " + d);
            }
            out.append(BigDecimal.valueOf(d).toPlainString());
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException(
                "Cannot serialize " + value.getClass().getName() + " to JSON");
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    // U+2028 and U+2029 are escaped too: they are legal in JSON
                    // but terminate a line in JavaScript.
                    if (c < 0x20 || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** The input was not valid JSON. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }
}
