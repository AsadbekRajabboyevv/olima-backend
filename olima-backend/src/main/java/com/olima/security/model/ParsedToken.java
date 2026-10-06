package com.olima.security.model;

public record ParsedToken(AuthenticatedUser user, int version) {}
