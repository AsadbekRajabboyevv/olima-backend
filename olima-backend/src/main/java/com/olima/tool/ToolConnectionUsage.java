package com.olima.tool;

import java.util.UUID;

/**
 * Integration moduli uchun tor, faqat o'qiydigan shartnoma: ulanish nechta vositada ishlatilishi.
 *
 * <p>{@link ToolService} ishlatilmaydi, chunki u o'zi {@code IntegrationConnectionService} ga
 * bog'liq — aylanma bog'liqlik bo'lmasligi uchun alohida port.
 */
public interface ToolConnectionUsage {

  long countToolsUsing(UUID organizationId, String connectionName);
}
