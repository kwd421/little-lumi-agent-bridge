package io.github.kwd421.lumitoolbridge.security;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/** Blocks local/private fetch targets before delegating to Ollama's remote fetch API. */
public final class UrlGuard {
    private UrlGuard() {}

    public static URI requirePublicHttpUrl(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("url is required");
        if (raw.length() > 4096) throw new IllegalArgumentException("URL is too long");
        URI uri;
        try { uri = URI.create(raw.trim()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid URL"); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("URLs containing credentials are not allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("URL host is missing");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new IllegalArgumentException("Only standard web ports 80 and 443 are allowed");
        }

        String ascii;
        try { ascii = IDN.toASCII(host).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid URL host"); }
        if (ascii.equals("localhost") || ascii.endsWith(".localhost") || ascii.endsWith(".local")
                || ascii.endsWith(".internal") || ascii.equals("metadata.google.internal")) {
            throw new IllegalArgumentException("Local or private URL targets are not allowed");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(ascii);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Local or private IP targets are not allowed");
                }
                byte[] bytes = address.getAddress();
                if (bytes.length == 4) {
                    int a = Byte.toUnsignedInt(bytes[0]);
                    int b = Byte.toUnsignedInt(bytes[1]);
                    if (a == 100 && b >= 64 && b <= 127) {
                        throw new IllegalArgumentException("Carrier-grade NAT targets are not allowed");
                    }
                    if (a == 169 && b == 254) {
                        throw new IllegalArgumentException("Link-local targets are not allowed");
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            if (looksLikeIpLiteral(ascii)) {
                throw new IllegalArgumentException("Invalid or unresolved IP target");
            }
        }
        return uri;
    }

    private static boolean looksLikeIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true;
        for (int i = 0; i < host.length(); i++) {
            char ch = host.charAt(i);
            if (!(ch == '.' || Character.isDigit(ch))) return false;
        }
        return true;
    }
}
