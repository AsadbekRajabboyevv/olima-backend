package com.olima.auth;

public interface LoginAttemptService {

  void checkNotLocked(String username);

  void recordFailure(String username);

  void recordSuccess(String username);
}
