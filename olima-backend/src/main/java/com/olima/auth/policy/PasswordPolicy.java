package com.olima.auth.policy;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.security.config.SecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

  private final SecurityProperties.Password config;

  public PasswordPolicy(SecurityProperties properties) {
    this.config = properties.password();
  }

  public void validate(String password) {
    if (password == null || password.length() < config.minLength()) {
      throw new BusinessException(
          ErrorCode.WEAK_PASSWORD,
          "Parol kamida " + config.minLength() + " belgidan iborat bo'lishi kerak");
    }
    if (config.requireLetterAndDigit()
        && (password.chars().noneMatch(Character::isLetter)
            || password.chars().noneMatch(Character::isDigit))) {
      throw new BusinessException(
          ErrorCode.WEAK_PASSWORD, "Parolda kamida bitta harf va bitta raqam bo'lishi kerak");
    }
  }
}
