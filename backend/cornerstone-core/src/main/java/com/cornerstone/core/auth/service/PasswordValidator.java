package com.cornerstone.core.auth.service;

public interface PasswordValidator {

    void validate(String password);

    boolean meetsRequirements(String password);
}
