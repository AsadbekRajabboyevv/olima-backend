package com.olima.complaint;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.NotFoundException;
import com.olima.complaint.dto.ComplaintResponse;
import com.olima.complaint.enums.ComplaintStatus;
import com.olima.complaint.event.ComplaintConfirmedEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

  private static final Set<ComplaintStatus> CONFIRMABLE =
      EnumSet.of(ComplaintStatus.DRAFT, ComplaintStatus.PENDING_CONFIRMATION);

  private final ComplaintRepository complaintRepository;
  private final ApplicationEventPublisher events;

  @Override
  @Transactional
  public ComplaintResponse createDraft(
      UUID orgId, UUID convId, String subject, String description, String category) {
    ComplaintEntity complaint =
        ComplaintEntity.builder()
            .organizationId(orgId)
            .conversationId(convId)
            .subject(subject)
            .description(description)
            .category(category)
            .status(ComplaintStatus.DRAFT)
            .build();
    ComplaintEntity saved = complaintRepository.save(complaint);
    return ComplaintResponse.of(saved);
  }

  private ComplaintEntity confirm(UUID complaintId) {
    ComplaintEntity complaint = require(complaintId);
    if (!CONFIRMABLE.contains(complaint.getStatus())) {
      BusinessException e =
          new BusinessException(
              ErrorCode.INVALID_STATE,
              "Murojaat allaqachon ko'rib chiqilgan (holati: " + complaint.getStatus() + ")");
      log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw e;
    }
    complaint.setStatus(ComplaintStatus.CONFIRMED);
    ComplaintEntity saved = complaintRepository.save(complaint);
    events.publishEvent(
        new ComplaintConfirmedEvent(
            saved.getId(),
            saved.getOrganizationId(),
            saved.getSubject(),
            saved.getDescription(),
            saved.getCategory()));
    return saved;
  }

  @Override
  @Transactional
  public ComplaintResponse confirmComplaint(UUID complaintId) {
    ComplaintResponse response = ComplaintResponse.of(confirm(complaintId));
    return response;
  }

  @Override
  @Transactional
  public void markSubmitted(UUID complaintId) {
    complaintRepository
        .findById(complaintId)
        .ifPresent(
            c -> {
              if (c.getStatus() == ComplaintStatus.CONFIRMED) {
                c.setStatus(ComplaintStatus.SUBMITTED);
                complaintRepository.save(c);
              }
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ComplaintResponse> findByOrganization(UUID orgId, Pageable pageable) {
    Page<ComplaintResponse> page =
        complaintRepository.findByOrganizationId(orgId, pageable).map(ComplaintResponse::of);
    return page;
  }

  private ComplaintEntity require(UUID id) {
    ComplaintEntity entity =
        complaintRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  NotFoundException e = new NotFoundException("get.complaint_not_found");
                  log.error("Error {} {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
                  return e;
                });
    return entity;
  }

  @Override
  @Transactional(readOnly = true)
  public ComplaintResponse getComplaint(UUID id) {
    ComplaintResponse response = ComplaintResponse.of(require(id));
    return response;
  }
}
