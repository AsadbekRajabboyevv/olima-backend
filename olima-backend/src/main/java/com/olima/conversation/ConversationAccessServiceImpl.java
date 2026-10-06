package com.olima.conversation;

import com.olima.common.error.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationAccessServiceImpl implements ConversationAccessService {

  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ConversationRepository conversationRepository;

  @Override
  public String newAccessToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return token;
  }

  @Override
  public String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  @Override
  public void verify(
      UUID conversationId, UUID organizationId, String accessToken, boolean requireToken) {
    var view = conversationRepository.findAccessView(conversationId).orElseThrow(this::notFound);

    if (!view.getOrganizationId().equals(organizationId)) {
      throw notFound();
    }
    if (requireToken && !tokenMatches(view.getAccessTokenHash(), accessToken)) {
      throw notFound();
    }
  }

  private boolean tokenMatches(String storedHash, String presentedToken) {
    if (storedHash == null || presentedToken == null || presentedToken.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        storedHash.getBytes(StandardCharsets.US_ASCII),
        hash(presentedToken.trim()).getBytes(StandardCharsets.US_ASCII));
  }

  private NotFoundException notFound() {
    NotFoundException e = new NotFoundException("verify.conversation_not_found");
    log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
    return e;
  }
}
