package com.olima.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olima.common.error.NotFoundException;
import com.olima.conversation.dto.ConversationAccessView;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationAccessServiceTest {

  private final UUID orgA = UUID.randomUUID();
  private final UUID orgB = UUID.randomUUID();
  private final UUID conversationId = UUID.randomUUID();

  private ConversationRepository repository;
  private ConversationAccessService service;

  @BeforeEach
  void setUp() {
    repository = mock(ConversationRepository.class);
    service = new ConversationAccessServiceImpl(repository);
  }

  private void storedConversation(UUID orgId, String tokenHash) {
    when(repository.findAccessView(conversationId))
        .thenReturn(
            Optional.of(
                new ConversationAccessView() {
                  @Override
                  public UUID getOrganizationId() {
                    return orgId;
                  }

                  @Override
                  public String getAccessTokenHash() {
                    return tokenHash;
                  }
                }));
  }

  @Test
  void otherOrganizationsConversationIsRejectedEvenWithoutToken() {
    storedConversation(orgB, null);

    assertThatThrownBy(() -> service.verify(conversationId, orgA, null, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void panelMayContinueOwnOrganizationsConversation() {
    storedConversation(orgA, null);

    assertThatCode(() -> service.verify(conversationId, orgA, null, false))
        .doesNotThrowAnyException();
  }

  @Test
  void widgetNeedsMatchingToken() {
    String token = service.newAccessToken();
    storedConversation(orgA, service.hash(token));

    assertThatCode(() -> service.verify(conversationId, orgA, token, true))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> service.verify(conversationId, orgA, service.newAccessToken(), true))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.verify(conversationId, orgA, null, true))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void widgetCannotContinuePanelOrTelegramConversation() {
    storedConversation(orgA, null);

    assertThatThrownBy(() -> service.verify(conversationId, orgA, "anything", true))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void unknownConversationIsNotFound() {
    when(repository.findAccessView(conversationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(conversationId, orgA, null, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void tokensAreRandomAndOnlyHashIsDeterministic() {
    String first = service.newAccessToken();
    String second = service.newAccessToken();

    assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(40);
    assertThat(service.hash(first)).isEqualTo(service.hash(first)).hasSize(64).isNotEqualTo(first);
  }
}
