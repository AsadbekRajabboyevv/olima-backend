package com.olima.integration.enums;

public enum AuthType {
  NONE,

  BASIC,

  BEARER,

  API_KEY,

  LOGIN,

  OAUTH2_CLIENT_CREDENTIALS;

  public boolean issuesTokens() {
    return this == LOGIN || this == OAUTH2_CLIENT_CREDENTIALS;
  }

  public boolean needsUsernameAndPassword() {
    return this == BASIC || this == LOGIN || this == OAUTH2_CLIENT_CREDENTIALS;
  }

  public boolean needsSecret() {
    return this == BEARER || this == API_KEY;
  }
}
