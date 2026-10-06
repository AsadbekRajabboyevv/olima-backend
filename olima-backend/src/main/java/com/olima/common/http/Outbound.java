package com.olima.common.http;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record Outbound(
    @DefaultValue("false") boolean allowPrivateNetworks,
    @DefaultValue List<String> allowedHosts,
    @DefaultValue List<String> blockedHosts,
    @DefaultValue({"http", "https"}) List<String> allowedSchemes) {}
