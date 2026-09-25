package com.epayethiopia.epay;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.epayethiopia.epay.exception.EpayConfigException;
import com.epayethiopia.epay.exception.EpayValidationException;
import com.epayethiopia.epay.exception.EpayWebhookSignatureException;
import com.epayethiopia.epay.internal.Json;
import com.epayethiopia.epay.model.WebhookEvent;

/**
 * Webhook signature verification and event parsing. Thread-safe.
 *
 * <pre>{@code
 * WebhookEvent event = epay.webhooks().constructEvent(rawBody, request.getHeader(Webhooks.SIGNATURE_HEADER));
 * }</pre>
 *
 * <p>Always pass the <strong>raw</strong> request body. Re-serializing a parsed
 * payload can reorder keys or change whitespace, which changes the digest and
 * rejects valid deliveries.
 */
public final class Webhooks {

    /** The header ePay signs every delivery with. */
    public static final String SIGNATURE_HEADER = "X-Epay-Signature";

    /** Every webhook event name this SDK version documents. */
    public static final List<String> EVENT_TYPES = List.of(
            "payment.success",
            "payment.failed",
            "payment.cancelled",
            "payment.refunding",
            "payment.refunded",
            "payment.reversed");

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]+$");
    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    Webhooks(String secret) {
        this.secret = secret == null || secret.isEmpty() ? null : secret;
    }

    /** A verifier for a specific secret, independent of any client. */
    public static Webhooks withSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new EpayConfigException("webhook secret must not be empty");
        }
        return new Webhooks(secret);
    }

    /** Whether a webhook secret is configured. */
    public boolean isConfigured() {
        return secret != null;
    }

    /** The expected signature for a payload, as lowercase hex. Useful for test fixtures. */
    public String sign(byte[] payload) {
        return HexFormat.of().formatHex(hmac(requireSecret(), payload));
    }

    /** The expected signature for a UTF-8 payload, as lowercase hex. */
    public String sign(String payload) {
        return sign(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks a signature header against the raw body, in constant time.
     * Accepts both a bare hex digest and the {@code sha256=} prefixed form.
     */
    public boolean verify(byte[] payload, String signatureHeader) {
        byte[] received = decodeSignature(signatureHeader);
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(hmac(requireSecret(), payload), received);
    }

    /** Checks a signature header against a UTF-8 body, in constant time. */
    public boolean verify(String payload, String signatureHeader) {
        return verify(payload.getBytes(StandardCharsets.UTF_8), signatureHeader);
    }

    /**
     * Verifies a delivery and returns the decoded event.
     *
     * <p>This is the entry point to use in a webhook handler: it fails closed, so
     * any event it returns had a valid signature.
     *
     * @throws EpayWebhookSignatureException if the header is missing or does not match
     * @throws EpayValidationException if the verified body is not a JSON object
     * @throws EpayConfigException if no webhook secret is configured
     */
    public WebhookEvent constructEvent(byte[] payload, String signatureHeader) {
        byte[] expected = hmac(requireSecret(), payload);
        byte[] received = decodeSignature(signatureHeader);
        if (received == null) {
            throw new EpayWebhookSignatureException(String.format(Locale.ROOT,
                    "Missing or malformed %s header. Expected \"%s<hex>\".", SIGNATURE_HEADER, SIGNATURE_PREFIX));
        }
        if (!MessageDigest.isEqual(expected, received)) {
            throw new EpayWebhookSignatureException(SIGNATURE_HEADER
                    + " does not match the request body. Verify you are using the raw request body "
                    + "and the webhook secret from Developers -> Webhooks.");
        }

        Object parsed;
        try {
            parsed = Json.parse(new String(payload, StandardCharsets.UTF_8));
        } catch (Json.JsonException e) {
            throw new EpayValidationException("Webhook body passed verification but is not valid JSON.", e);
        }
        if (!(parsed instanceof Map<?, ?>)) {
            throw new EpayValidationException("Webhook body passed verification but is not a JSON object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) parsed;
        return Decoder.webhookEvent(json);
    }

    /** Verifies a UTF-8 delivery and returns the decoded event. */
    public WebhookEvent constructEvent(String payload, String signatureHeader) {
        return constructEvent(payload.getBytes(StandardCharsets.UTF_8), signatureHeader);
    }

    @Override
    public String toString() {
        return "Webhooks(configured=" + isConfigured() + ")";
    }

    /** Reduces a header to raw digest bytes, or null when it is absent or not hex. */
    private static byte[] decodeSignature(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.strip();
        if (trimmed.regionMatches(true, 0, SIGNATURE_PREFIX, 0, SIGNATURE_PREFIX.length())) {
            trimmed = trimmed.substring(SIGNATURE_PREFIX.length());
        }
        if (trimmed.isEmpty() || trimmed.length() % 2 != 0 || !HEX.matcher(trimmed).matches()) {
            return null;
        }
        return HexFormat.of().parseHex(trimmed);
    }

    private String requireSecret() {
        if (secret == null) {
            throw new EpayConfigException("No webhook secret configured. Call webhookSecret(…) on the "
                    + "client builder, set EPAY_WEBHOOK_SECRET, or use Webhooks.withSecret(…).");
        }
        return secret;
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandatory on every Java platform, and any non-empty
            // key is valid for it, so this is unreachable in practice.
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
