package com.olima.user;

import com.olima.user.dto.CreateUserRequest;
import com.olima.user.dto.ResetPasswordRequest;
import com.olima.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController implements UserApi {

  private final UserService userService;

  @Override
  @GetMapping
  public ResponseEntity<List<UserResponse>> findAll() {
    return ResponseEntity.ok(userService.findAll());
  }

  @Override
  @PostMapping
  public ResponseEntity<UserResponse> create(@RequestBody @Valid CreateUserRequest request) {
    return ResponseEntity.ok(userService.create(request));
  }

  @Override
  @PutMapping("/{id}/password")
  public ResponseEntity<UserResponse> resetPassword(
      @PathVariable UUID id, @RequestBody @Valid ResetPasswordRequest request) {
    return ResponseEntity.ok(userService.resetPassword(id, request));
  }

  @Override
  @PatchMapping("/{id}/enabled")
  public ResponseEntity<UserResponse> setEnabled(
      @PathVariable UUID id, @RequestParam boolean enabled) {
    return ResponseEntity.ok(userService.setEnabled(id, enabled));
  }

  @Override
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    userService.delete(id);
    return ResponseEntity.ok().build();
  }
}
