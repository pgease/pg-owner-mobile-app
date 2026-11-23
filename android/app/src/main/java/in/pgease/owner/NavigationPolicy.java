package in.pgease.owner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a WebView navigation stays inside the shell, opens another
 * app, opens the system browser, or is saved as a download.
 *
 * <p>Pure Java so it can be unit-tested without an Android runtime.
 */
public final class NavigationPolicy {

    public enum Decision {
        IN_APP,
        EXTERNAL_APP,
        EXTERNAL_BROWSER,
        DOWNLOAD
    }

    private static final String[] APP_HOSTS = {
        "app.pgease.com",
        "app.pgease.in",
        "localhost",
        "127.0.0.1",
        "10.0.2.2"
    };

    private static final String[] GATEWAY_SUFFIXES = {
        "easebuzz.in",
        "razorpay.com",
        "digio.in",
        "cashfree.com",
        "payu.in",
        "payumoney.com",
        "billdesk.com",
        "ccavenue.com",
        "stripe.com",
        "juspay.in",
        "phonepe.com",
        "cardinalcommerce.com",
        "arcot.com",
        "wibmo.com"
    };

    private static final String[] GATEWAY_HOSTS = {
        "ebz-static.s3.ap-south-1.amazonaws.com"
    };

    private static final String[] WHATSAPP_HOSTS = {
        "wa.me",
        "api.whatsapp.com",
        "chat.whatsapp.com",
        "web.whatsapp.com",
        "whatsapp.com"
    };

    private final Set<String> extraAppHosts;

    public NavigationPolicy(String... extraAppHosts) {
        Set<String> hosts = new HashSet<>();
        if (extraAppHosts != null) {
            for (String host : extraAppHosts) {
                if (host != null && !host.isEmpty()) {
                    hosts.add(host.toLowerCase(Locale.US));
                }
            }
        }
        this.extraAppHosts = Collections.unmodifiableSet(hosts);
    }

    public Decision decide(String rawUrl, boolean paymentFlow) {
        if (rawUrl == null) {
            return Decision.IN_APP;
        }
        String url = rawUrl.trim();
        if (url.isEmpty()) {
            return Decision.IN_APP;
        }
        String scheme = schemeOf(url);
        if (scheme == null) {
            return Decision.IN_APP;
        }
        if (
            scheme.equals("about") ||
            scheme.equals("blob") ||
            scheme.equals("data") ||
            scheme.equals("javascript") ||
            scheme.equals("file")
        ) {
            return Decision.IN_APP;
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Decision.EXTERNAL_APP;
        }

        String host = hostOf(url);
        if (host == null || host.isEmpty()) {
            return Decision.EXTERNAL_BROWSER;
        }
        if (isPdfPath(url)) {
            return Decision.DOWNLOAD;
        }
        if (isWhatsAppHost(host)) {
            return Decision.EXTERNAL_APP;
        }
        if (isAppHostName(host) || isGatewayHostName(host)) {
            return Decision.IN_APP;
        }
        if (paymentFlow) {
            return Decision.IN_APP;
        }
        return Decision.EXTERNAL_BROWSER;
    }

    public boolean isAppHost(String rawUrl) {
        String host = hostOf(rawUrl);
        return host != null && isAppHostName(host);
    }

    public boolean isGatewayHost(String rawUrl) {
        String host = hostOf(rawUrl);
        return host != null && isGatewayHostName(host);
    }

    /**
     * Keeps the web app's native-shell flag on deep links into our own host.
     * Existing {@code app=} values are left untouched, including {@code app=0}.
     */
    public String withAppFlag(String rawUrl) {
        if (rawUrl == null || !isAppHost(rawUrl)) {
            return rawUrl;
        }
        int hash = rawUrl.indexOf('#');
        String before = hash >= 0 ? rawUrl.substring(0, hash) : rawUrl;
        String fragment = hash >= 0 ? rawUrl.substring(hash) : "";
        int query = before.indexOf('?');
        if (query >= 0) {
            String[] parts = before.substring(query + 1).split("&");
            for (String part : parts) {
                if (part.equals("app") || part.startsWith("app=")) {
                    return rawUrl;
                }
            }
            return before + "&app=1" + fragment;
        }
        return before + "?app=1" + fragment;
    }

    private boolean isAppHostName(String host) {
        for (String known : APP_HOSTS) {
            if (host.equals(known)) {
                return true;
            }
        }
        return extraAppHosts.contains(host);
    }

    private boolean isGatewayHostName(String host) {
        for (String exact : GATEWAY_HOSTS) {
            if (host.equals(exact)) {
                return true;
            }
        }
        for (String suffix : GATEWAY_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWhatsAppHost(String host) {
        for (String known : WHATSAPP_HOSTS) {
            if (host.equals(known) || host.endsWith("." + known)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPdfPath(String url) {
        String path = url;
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.toLowerCase(Locale.US).endsWith(".pdf");
    }

    static String schemeOf(String url) {
        int sep = url.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        for (int i = 0; i < sep; i++) {
            char c = url.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '+' || c == '.' || c == '-';
            if (!ok) {
                return null;
            }
        }
        return url.substring(0, sep).toLowerCase(Locale.US);
    }

    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return null;
        }
        int hostStart = start + 3;
        if (hostStart >= url.length()) {
            return null;
        }
        if (url.charAt(hostStart) == '[') {
            int end = url.indexOf(']', hostStart);
            if (end < 0) {
                return null;
            }
            return url.substring(hostStart, end + 1).toLowerCase(Locale.US);
        }
        int hostEnd = hostStart;
        while (hostEnd < url.length()) {
            char c = url.charAt(hostEnd);
            if (c == '/' || c == '?' || c == '#' || c == ':') {
                break;
            }
            hostEnd++;
        }
        String host = url.substring(hostStart, hostEnd);
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        return host.toLowerCase(Locale.US);
    }
}
