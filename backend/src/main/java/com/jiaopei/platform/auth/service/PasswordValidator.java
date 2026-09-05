package com.jiaopei.platform.auth.service;

public interface PasswordValidator {

    void validate(String password);

    boolean meetsRequirements(String password);
}
