package com.cornerstone.core.auth.service.impl;

import com.cornerstone.core.auth.service.PasswordValidator;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.system.service.SystemConfigService;
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
                "密码强度未达到「" + strengthLevelName(level) + "」等级要求");
        }
    }

    private String strengthLevelName(String level) {
        return switch (level == null ? "" : level) {
            case "simple" -> "简单";
            case "medium" -> "中等";
            case "complex" -> "复杂";
            default -> "中等";
        };
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
