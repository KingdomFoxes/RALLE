package org.kingdomfoxes.ralle.client;

import java.net.IDN;
import java.util.Locale;

/** Strict Wynncraft hostname recognition shared by client-only integrations. */
public final class WynncraftHost {
    private WynncraftHost() {}

    public static boolean matches(String address) {
        var host = normalize(address);
        return domainOrSubdomain(host, "wynncraft.com") || domainOrSubdomain(host, "wynncraft.net");
    }

    public static String normalize(String address) {
        if (address == null) return "";
        var value = address.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) return "";
            value = value.substring(1, close);
        } else {
            int colon = value.lastIndexOf(':');
            if (colon >= 0 && value.indexOf(':') == colon) value = value.substring(0, colon);
        }
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        try {
            return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean domainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
