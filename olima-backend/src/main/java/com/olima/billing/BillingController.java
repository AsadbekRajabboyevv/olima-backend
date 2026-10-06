package com.olima.billing;

import com.olima.billing.dto.InvoiceResponse;
import com.olima.billing.dto.OrganizationBillingRow;
import com.olima.billing.dto.RatesResponse;
import com.olima.billing.dto.TokenRateRequest;
import com.olima.billing.dto.TokenRateResponse;
import com.olima.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillingController implements BillingApi {

  private final BillingService billingService;

  @Override
  @GetMapping("/api/v1/organizations/{id}/billing")
  @PreAuthorize("@tenant.canAccessOrganization(#id)")
  public ResponseEntity<InvoiceResponse> invoice(
      @PathVariable UUID id, @RequestParam(required = false) YearMonth month) {
    return ResponseEntity.ok(billingService.invoice(id, month));
  }

  @Override
  @GetMapping("/api/v1/super-admin/billing/summary")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<List<OrganizationBillingRow>> summary(
      @RequestParam(required = false) YearMonth month) {
    return ResponseEntity.ok(billingService.summary(month));
  }

  @Override
  @GetMapping("/api/v1/super-admin/billing/rates")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<RatesResponse> rates() {
    return ResponseEntity.ok(billingService.rates());
  }

  @Override
  @PutMapping("/api/v1/super-admin/billing/rates/default")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<TokenRateResponse> updateDefaultRate(
      @RequestBody @Valid TokenRateRequest request,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(billingService.updateDefaultRate(request, user.username()));
  }

  @Override
  @PutMapping("/api/v1/super-admin/billing/rates/organizations/{organizationId}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<TokenRateResponse> setOrganizationRate(
      @PathVariable UUID organizationId,
      @RequestBody @Valid TokenRateRequest request,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        billingService.setOrganizationRate(organizationId, request, user.username()));
  }

  @Override
  @DeleteMapping("/api/v1/super-admin/billing/rates/organizations/{organizationId}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<Void> removeOrganizationRate(@PathVariable UUID organizationId) {
    billingService.removeOrganizationRate(organizationId);
    return ResponseEntity.noContent().build();
  }
}
