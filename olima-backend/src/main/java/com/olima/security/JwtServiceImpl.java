package com.olima.security;

import com.olima.security.config.SecurityProperties;
import com.olima.security.model.AuthenticatedUser;
import com.olima.security.model.IssuedToken;
import com.olima.security.model.ParsedToken;
import com.olima.user.UserEntity;
import com.olima.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_ORG_ID = "orgId";
  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_VERSION = "ver";

  private final SecretKey key;
  private final SecurityProperties.Jwt config;

  public JwtServiceImpl(SecurityProperties properties) {
    this.config = properties.jwt();
    this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public IssuedToken generateToken(UserEntity user) {
    Instant now = Instant.now();
    Instant expiry = now.plus(config.accessTokenTtl());

    var builder =
        Jwts.builder()
            .issuer(config.issuer())
            .subject(user.getUsername())
            .id(UUID.randomUUID().toString())
            .claim(CLAIM_USER_ID, user.getId().toString())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_VERSION, user.getTokenVersion())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry));

    if (user.getOrganizationId() != null) {
      builder.claim(CLAIM_ORG_ID, user.getOrganizationId().toString());
    }
    IssuedToken token = new IssuedToken(builder.signWith(key).compact(), expiry);
    return token;
  }

  @Override
  public ParsedToken parseToken(String token) throws JwtException {
    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(config.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();

    UUID userId = UUID.fromString(claims.get(CLAIM_USER_ID, String.class));
    String username = claims.getSubject();
    UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    if (role == UserRole.WIDGET) {
      JwtException e = new JwtException("Widget role cannot be carried by a user token");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    String orgIdStr = claims.get(CLAIM_ORG_ID, String.class);
    UUID organizationId = orgIdStr != null ? UUID.fromString(orgIdStr) : null;
    Integer version = claims.get(CLAIM_VERSION, Integer.class);

    ParsedToken parsed =
        new ParsedToken(
            new AuthenticatedUser(userId, username, role, organizationId),
            version != null ? version : 0);
    return parsed;
  }
}
