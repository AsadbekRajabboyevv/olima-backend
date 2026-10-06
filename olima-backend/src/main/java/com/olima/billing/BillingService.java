package com.olima.billing;

import com.olima.billing.dto.InvoiceResponse;
import com.olima.billing.dto.OrganizationBillingRow;
import com.olima.billing.dto.RatesResponse;
import com.olima.billing.dto.TokenRateRequest;
import com.olima.billing.dto.TokenRateResponse;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface BillingService {

  BigDecimal cost(UUID organizationId, int promptTokens, int completionTokens);

  RatesResponse rates();

  TokenRateResponse updateDefaultRate(TokenRateRequest request, String username);

  TokenRateResponse setOrganizationRate(
      UUID organizationId, TokenRateRequest request, String username);

  void removeOrganizationRate(UUID organizationId);

  InvoiceResponse invoice(UUID organizationId, YearMonth month);

  List<OrganizationBillingRow> summary(YearMonth month);
}
