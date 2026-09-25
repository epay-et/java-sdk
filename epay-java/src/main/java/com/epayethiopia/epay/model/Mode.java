package com.epayethiopia.epay.model;

import java.util.Optional;

/** Whether a key or event belongs to live traffic or the sandbox. */
public enum Mode {
    /** Real payments. */
    LIVE("live"),
    /** Test payments that move no money. */
    SANDBOX("sandbox");

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    /** The wire value, such as {@code "live"}. */
    public String value() {
        return value;
    }

    /** Looks up a mode by its wire value. */
    public static Optional<Mode> fromValue(String value) {
        for (Mode mode : values()) {
            if (mode.value.equals(value)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
