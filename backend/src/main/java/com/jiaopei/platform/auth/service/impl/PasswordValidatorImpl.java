package com.jiaopei.platform.auth.service.impl;

import com.jiaopei.platform.auth.service.PasswordValidator;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.system.service.SystemConfigService;
import org.springframework.stereotype.Service;

@Service
public class PasswordValidatorImpl implements PasswordValidator {

    private final SystemConfigService systemConfigService;

    public PasswordValidatorImpl(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Override
    public void validate(String password) {
        if (!meetsRequirements(password)) {
            String level = systemConfigService.getConfigValue("auth.password.strength_level", "medium");
            throw new BusinessException(ResultCode.VALIDATION_ERROR,
                "Password does not meet " + level + " strength requirements");
        }
    }

    @Override
    public boolean meetsRequirements(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        Integer minLength = systemConfigService.getIntConfig("auth.password.min_length", 8);
        Integer maxLength = systemConfigService.getIntConfig("auth.password.max_length", 128);

        if (password.length() < minLength || password.length() > maxLength) {
            return false;
        }

        String level = systemConfigService.getConfigValue("auth.password.strength_level", "medium");

        return switch (level) {
            case "simple" -> true;
            case "medium" -> hasMediumStrength(password);
            case "complex" -> hasComplexStrength(password);
            default -> hasMediumStrength(password);
        };
    }

    private boolean hasMediumStrength(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    private boolean hasComplexStrength(String password) {
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        int categories = 0;
        if (hasLower) categories++;
        if (hasUpper) categories++;
        if (hasDigit) categories++;
        if (hasSpecial) categories++;

        return categories >= 3;
    }
}
