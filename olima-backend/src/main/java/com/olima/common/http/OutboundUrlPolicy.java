package com.olima.common.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OutboundUrlPolicy {

  private final Outbound outbound;

  public OutboundUrlPolicy(HttpClientProperties properties) {
    this.outbound = properties.outbound();
  }

  public URI requireAllowed(String url) {
    if (url == null || url.isBlank()) {
      throw new OutboundRequestException("URL is required");
    }
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException e) {
      throw new OutboundRequestException("Invalid URL: " + url);
    }
    return requireAllowed(uri);
  }

  public URI requireAllowed(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null || outbound.allowedSchemes().stream().noneMatch(scheme::equalsIgnoreCase)) {
      throw new OutboundRequestException("URL scheme is not allowed: " + scheme);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new OutboundRequestException("Invalid URL: missing host");
    }
    if (uri.getUserInfo() != null) {
      throw new OutboundRequestException("Credentials inside URL are not allowed");
    }
    String normalizedHost = normalize(host);
    if (matchesAny(normalizedHost, outbound.blockedHosts())) {
      throw new OutboundRequestException("Host is blocked by policy: " + host);
    }
    if (outbound.allowPrivateNetworks() || matchesAny(normalizedHost, outbound.allowedHosts())) {
      return uri;
    }
    if (normalizedHost.equals("localhost")
        || normalizedHost.endsWith(".localhost")
        || normalizedHost.endsWith(".internal")
        || normalizedHost.endsWith(".local")) {
      throw new OutboundRequestException("Internal hosts are not allowed: " + host);
    }
    for (InetAddress address : resolve(normalizedHost)) {
      if (isInternalAddress(address)) {
        throw new OutboundRequestException("Internal/private network addresses are not allowed");
      }
    }
    return uri;
  }

  public boolean isAllowed(String url) {
    try {
      requireAllowed(url);
      return true;
    } catch (OutboundRequestException e) {
      return false;
    }
  }

  private static InetAddress[] resolve(String host) {
    try {
      return InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      throw new OutboundRequestException("Could not resolve host: " + host);
    }
  }

  private static String normalize(String host) {
    String h = host.toLowerCase(Locale.ROOT);
    if (h.startsWith("[") && h.endsWith("]")) {
      h = h.substring(1, h.length() - 1);
    }
    return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
  }

  static boolean matchesAny(String host, List<String> patterns) {
    for (String raw : patterns) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String pattern = raw.trim().toLowerCase(Locale.ROOT);
      if (pattern.startsWith("*.")) {
        String suffix = pattern.substring(1);
        if (host.endsWith(suffix) && host.length() > suffix.length()) {
          return true;
        }
      } else if (host.equals(pattern)) {
        return true;
      }
    }
    return false;
  }

  static boolean isInternalAddress(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] b = address.getAddress();
    if (address instanceof Inet6Address) {
      if ((b[0] & 0xFE) == 0xFC) {
        return true;
      }

      boolean mapped = true;
      for (int i = 0; i < 10; i++) {
        mapped &= b[i] == 0;
      }
      if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
        try {
          return isInternalAddress(
              InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
        } catch (UnknownHostException e) {
          return true;
        }
      }
      return false;
    }
    if (!(address instanceof Inet4Address)) {
      return true;
    }
    int first = b[0] & 0xFF;
    int second = b[1] & 0xFF;
    return first == 0
        || (first == 100 && second >= 64 && second <= 127)
        || (first == 192 && second == 0 && (b[2] & 0xFF) == 0)
        || (first == 198 && (second == 18 || second == 19))
        || first >= 240;
  }
}
