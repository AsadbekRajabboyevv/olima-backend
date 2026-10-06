package com.olima.billing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.olima.billing.dto.DailyCost;
import com.olima.billing.dto.InvoiceResponse;
import com.olima.billing.dto.OrganizationBillingRow;
import com.olima.billing.dto.OrganizationUsage;
import com.olima.billing.dto.RatesResponse;
import com.olima.billing.dto.TokenRateRequest;
import com.olima.billing.dto.TokenRateResponse;
import com.olima.billing.dto.UsageTotals;
import com.olima.organization.OrganizationService;
import com.olima.usage.config.QuotaProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingServiceImpl implements BillingService {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  private final TokenRateRepository rateRepository;
  private final BillingUsageRepository usageRepository;
  private final OrganizationService organizationService;
  private final QuotaProperties quotaProperties;
  private final Cache<UUID, TokenRateEntity> rateCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(10_000).build();

  public BillingServiceImpl(
      TokenRateRepository rateRepository,
      BillingUsageRepository usageRepository,
      OrganizationService organizationService,
      QuotaProperties quotaProperties) {
    this.rateRepository = rateRepository;
    this.usageRepository = usageRepository;
    this.organizationService = organizationService;
    this.quotaProperties = quotaProperties;
  }

  @Override
  public BigDecimal cost(UUID organizationId, int promptTokens, int completionTokens) {
    TokenRateEntity rate = rateCache.get(organizationId, this::effectiveRate);
    BigDecimal result =
        rate.getInputPricePerMillion()
            .multiply(BigDecimal.valueOf(promptTokens))
            .add(rate.getOutputPricePerMillion().multiply(BigDecimal.valueOf(completionTokens)))
            .divide(MILLION, 4, RoundingMode.HALF_UP);
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public RatesResponse rates() {
    List<TokenRateEntity> custom = rateRepository.findByOrganizationIdIsNotNull();
    Map<UUID, String> names =
        organizationService.findNames(
            custom.stream().map(TokenRateEntity::getOrganizationId).toList());
    List<TokenRateResponse> organizationRates =
        custom.stream()
            .map(r -> toResponse(r, names.get(r.getOrganizationId())))
            .sorted(
                Comparator.comparing(
                    TokenRateResponse::organizationName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    RatesResponse response = new RatesResponse(toResponse(defaultRate(), null), organizationRates);
    return response;
  }

  @Override
  @Transactional
  public TokenRateResponse updateDefaultRate(TokenRateRequest request, String username) {
    TokenRateEntity rate =
        rateRepository.findByOrganizationIdIsNull().orElseGet(TokenRateEntity::new);
    apply(rate, request, username);
    TokenRateEntity saved = rateRepository.save(rate);
    rateCache.invalidateAll();
    TokenRateResponse response = toResponse(saved, null);
    return response;
  }

  @Override
  @Transactional
  public TokenRateResponse setOrganizationRate(
      UUID organizationId, TokenRateRequest request, String username) {
    String organizationName = organizationName(organizationId);
    TokenRateEntity rate =
        rateRepository
            .findByOrganizationId(organizationId)
            .orElseGet(() -> TokenRateEntity.builder().organizationId(organizationId).build());
    apply(rate, request, username);
    TokenRateEntity saved = rateRepository.save(rate);
    rateCache.invalidate(organizationId);
    TokenRateResponse response = toResponse(saved, organizationName);
    return response;
  }

  @Override
  @Transactional
  public void removeOrganizationRate(UUID organizationId) {
    rateRepository.findByOrganizationId(organizationId).ifPresent(rateRepository::delete);
    rateCache.invalidate(organizationId);
  }

  @Override
  @Transactional(readOnly = true)
  public InvoiceResponse invoice(UUID organizationId, YearMonth month) {
    String organizationName = organizationName(organizationId);
    YearMonth period = month != null ? month : YearMonth.now(quotaProperties.zone());
    Instant from = startOf(period);
    Instant to = startOf(period.plusMonths(1));

    UsageTotals totals = usageRepository.totals(organizationId, from, to);
    List<DailyCost> days =
        usageRepository.daily(organizationId, from, to, quotaProperties.zone().getId()).stream()
            .map(
                d ->
                    new DailyCost(
                        d.getDay(), d.getRequests(), d.getTokens(), money(d.getCostUzs())))
            .toList();
    Optional<TokenRateEntity> custom = rateRepository.findByOrganizationId(organizationId);
    TokenRateResponse currentRate =
        toResponse(
            custom.orElseGet(this::defaultRate),
            custom.isPresent() ? organizationName : null);

    InvoiceResponse response =
        new InvoiceResponse(
            organizationId,
            organizationName,
            period,
            totals.getRequests(),
            totals.getPromptTokens(),
            totals.getCompletionTokens(),
            totals.getPromptTokens() + totals.getCompletionTokens(),
            money(totals.getCostUzs()),
            currentRate,
            days);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public List<OrganizationBillingRow> summary(YearMonth month) {
    YearMonth period = month != null ? month : YearMonth.now(quotaProperties.zone());
    Map<UUID, OrganizationUsage> usage =
        usageRepository.byOrganization(startOf(period), startOf(period.plusMonths(1))).stream()
            .collect(Collectors.toMap(OrganizationUsage::getOrganizationId, Function.identity()));
    Set<UUID> customRates =
        rateRepository.findByOrganizationIdIsNotNull().stream()
            .map(TokenRateEntity::getOrganizationId)
            .collect(Collectors.toSet());

    List<OrganizationBillingRow> rows =
        organizationService.findAllNames().entrySet().stream()
            .map(
                org -> {
                  OrganizationUsage u = usage.get(org.getKey());
                  return new OrganizationBillingRow(
                      org.getKey(),
                      org.getValue(),
                      u != null ? u.getRequests() : 0,
                      u != null ? u.getTokens() : 0,
                      money(u != null ? u.getCostUzs() : null),
                      customRates.contains(org.getKey()));
                })
            .sorted(
                Comparator.comparing(OrganizationBillingRow::costUzs)
                    .reversed()
                    .thenComparing(
                        OrganizationBillingRow::organizationName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    return rows;
  }

  private TokenRateEntity effectiveRate(UUID organizationId) {
    return rateRepository.findByOrganizationId(organizationId).orElseGet(this::defaultRate);
  }

  private TokenRateEntity defaultRate() {
    return rateRepository
        .findByOrganizationIdIsNull()
        .orElseGet(
            () ->
                TokenRateEntity.builder()
                    .inputPricePerMillion(BigDecimal.ZERO)
                    .outputPricePerMillion(BigDecimal.ZERO)
                    .build());
  }

  private String organizationName(UUID organizationId) {
    return organizationService.findById(organizationId).name();
  }

  private void apply(TokenRateEntity rate, TokenRateRequest request, String username) {
    rate.setInputPricePerMillion(request.inputPricePerMillion());
    rate.setOutputPricePerMillion(request.outputPricePerMillion());
    rate.setUpdatedBy(username);
  }

  private TokenRateResponse toResponse(TokenRateEntity rate, String organizationName) {
    return new TokenRateResponse(
        rate.getOrganizationId(),
        organizationName,
        rate.getInputPricePerMillion(),
        rate.getOutputPricePerMillion(),
        rate.getOrganizationId() != null,
        rate.getUpdatedBy(),
        rate.getUpdatedAt());
  }

  private Instant startOf(YearMonth month) {
    return month.atDay(1).atStartOfDay(quotaProperties.zone()).toInstant();
  }

  private static BigDecimal money(BigDecimal value) {
    return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }
}
