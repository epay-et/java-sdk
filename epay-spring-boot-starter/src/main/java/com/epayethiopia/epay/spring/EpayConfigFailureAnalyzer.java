package com.epayethiopia.epay.spring;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import com.epayethiopia.epay.exception.EpayConfigException;

/** Turns a client configuration failure at startup into actionable advice. */
class EpayConfigFailureAnalyzer extends AbstractFailureAnalyzer<EpayConfigException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, EpayConfigException cause) {
        return new FailureAnalysis(
                "The ePay client could not be created: " + cause.getMessage(),
                "Set epay.api-key (or the EPAY_SECRET_KEY environment variable) to a secret key from "
                        + "Developers -> API Keys in the ePay dashboard. To start without the client, "
                        + "for example in tests, set epay.enabled=false.",
                cause);
    }
}
