package com.epayethiopia.epay.spring;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import com.epayethiopia.epay.Webhooks;
import com.epayethiopia.epay.exception.EpayValidationException;
import com.epayethiopia.epay.exception.EpayWebhookSignatureException;
import com.epayethiopia.epay.model.WebhookEvent;

/**
 * Resolves {@link EpayWebhook @EpayWebhook} {@link WebhookEvent} parameters.
 *
 * <p>Reads the body straight from the servlet request rather than through a
 * message converter. {@code @RequestBody} would deserialize and the bytes would be
 * gone, and re-serializing changes whitespace and key order, which changes the
 * HMAC and rejects valid deliveries.
 */
public class EpayWebhookArgumentResolver implements HandlerMethodArgumentResolver {

    private final Webhooks webhooks;

    public EpayWebhookArgumentResolver(Webhooks webhooks) {
        this.webhooks = webhooks;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(EpayWebhook.class)
                && WebhookEvent.class.equals(parameter.getParameterType());
    }

    @Override
    public WebhookEvent resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws IOException {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("@EpayWebhook requires a servlet request");
        }

        byte[] body = rawBody(request);
        String signature = request.getHeader(Webhooks.SIGNATURE_HEADER);

        try {
            return webhooks.constructEvent(body, signature);
        } catch (EpayWebhookSignatureException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        } catch (EpayValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * The request's bytes, exactly as sent.
     *
     * <p>If a logging filter wrapped the request in a
     * {@link ContentCachingRequestWrapper} and already drained the stream, the
     * copy it cached is used instead.
     */
    private static byte[] rawBody(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0) {
            ContentCachingRequestWrapper cached =
                    WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
            if (cached != null) {
                return cached.getContentAsByteArray();
            }
        }
        return body;
    }
}
