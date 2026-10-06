package com.olima.security.model;

public record UserSession(boolean enabled, int tokenVersion) {}
