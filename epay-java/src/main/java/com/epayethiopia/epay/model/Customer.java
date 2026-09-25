package com.epayethiopia.epay.model;

/**
 * Customer details on a verified receipt or webhook event. Any field may be null.
 *
 * @param name the customer's name
 * @param email the customer's email address
 * @param phone the customer's phone number
 */
public record Customer(String name, String email, String phone) {}
