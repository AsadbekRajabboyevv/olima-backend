package com.olima.user;

import com.olima.user.dto.CreateUserRequest;
import com.olima.user.dto.ResetPasswordRequest;
import com.olima.user.dto.UserResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {

  List<UserResponse> findAll();

  UserResponse create(CreateUserRequest request);

  UserResponse resetPassword(UUID id, ResetPasswordRequest request);

  UserResponse setEnabled(UUID id, boolean enabled);

  void delete(UUID id);
}
