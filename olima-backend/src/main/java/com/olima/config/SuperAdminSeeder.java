package com.olima.config;

import com.olima.auth.policy.PasswordPolicy;
import com.olima.security.config.SecurityProperties;
import com.olima.user.UserEntity;
import com.olima.user.UserRepository;
import com.olima.user.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final SecurityProperties securityProperties;

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
      return;
    }
    SecurityProperties.BootstrapAdmin admin = securityProperties.bootstrapAdmin();
    if (admin.username() == null
        || admin.username().isBlank()
        || admin.password() == null
        || admin.password().isBlank()) {
      log.warn(
          "No SUPER_ADMIN exists and app.security.bootstrap-admin.password is empty — "
              + "set SUPER_ADMIN_PASSWORD to create the first administrator");
      return;
    }
    passwordPolicy.validate(admin.password());
    if (userRepository.existsByUsername(admin.username())) {
      log.warn("Cannot seed SUPER_ADMIN: username '{}' is taken by another role", admin.username());
      return;
    }
    userRepository.save(
        UserEntity.builder()
            .username(admin.username())
            .passwordHash(passwordEncoder.encode(admin.password()))
            .role(UserRole.SUPER_ADMIN)
            .enabled(true)
            .build());
    log.info("Seeded initial super admin '{}'", admin.username());
  }
}
