package com.olima.user;

import com.olima.user.dto.CreateUserRequest;
import com.olima.user.dto.ResetPasswordRequest;
import com.olima.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public interface UserApi {

  @GetMapping
  ResponseEntity<List<UserResponse>> findAll();

  @PostMapping
  ResponseEntity<UserResponse> create(@RequestBody @Valid CreateUserRequest request);

  @PutMapping("/{id}/password")
  ResponseEntity<UserResponse> resetPassword(
      @PathVariable UUID id, @RequestBody @Valid ResetPasswordRequest request);

  @PatchMapping("/{id}/enabled")
  ResponseEntity<UserResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean enabled);

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id);
}
