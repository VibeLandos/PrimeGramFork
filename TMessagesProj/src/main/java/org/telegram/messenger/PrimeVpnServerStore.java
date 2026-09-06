package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PrimeGram: the list of VLESS/VMess/Trojan/Shadowsocks servers a person has added by hand -
 * what {@link vpn.sdk.VpnSDK}'s own cache (one config, last one wins) has no room for. Selecting an entry
 * here is what actually reaches VpnSDK.setCustomVlessConfig; this class only remembers what was
 * typed in and which one is meant to be active, the way a Happ-style client's server list does.
 */
public final class PrimeVpnServerStore {

    private static final String KEY_SERVERS = "primegram_vpn_servers";
    private static final String KEY_ACTIVE = "primegram_vpn_active_server";
    private static final String KEY_PING_MODE = "primegram_vpn_ping_mode";

    private static final Pattern SHARE_LINK = Pattern.compile(
            "(?i)(?:vless|vmess|trojan|ss|socks)://[^\\s<>\"'`]+");

    public static final class Server {
        public final String id;
        public final String name;
        public final String protocol;
        public final String rawUrl;
        public final long addedAt;
        /** -1 never checked, -2 unreachable, otherwise round-trip ms. */
        public long lastPingMs = -1;

        Server(String id, String name, String protocol, String rawUrl, long addedAt, long lastPingMs) {
            this.id = id;
            this.name = name;
            this.protocol = protocol;
            this.rawUrl = rawUrl;
            this.addedAt = addedAt;
            this.lastPingMs = lastPingMs;
        }
    }

    private PrimeVpnServerStore() {
    }

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    public static int getPingMode() {
        return prefs().getInt(KEY_PING_MODE, vpn.sdk.VpnSDK.DELAY_TCP);
    }

    public static void setPingMode(int mode) {
        prefs().edit().putInt(KEY_PING_MODE, mode).apply();
    }

    public static synchronized List<Server> getServers() {
        final List<Server> result = new ArrayList<>();
        try {
            final JSONArray arr = new JSONArray(prefs().getString(KEY_SERVERS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.getJSONObject(i);
                result.add(new Server(o.getString("id"), o.optString("name", ""),
                        o.optString("protocol", "vless"), o.getString("url"), o.optLong("addedAt", 0),
                        o.optLong("lastPingMs", -1)));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return result;
    }

    private static synchronized void save(List<Server> servers) {
        final JSONArray arr = new JSONArray();
        try {
            for (Server s : servers) {
                final JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("protocol", s.protocol);
                o.put("url", s.rawUrl);
                o.put("addedAt", s.addedAt);
                o.put("lastPingMs", s.lastPingMs);
                arr.put(o);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        prefs().edit().putString(KEY_SERVERS, arr.toString()).apply();
    }

    public static synchronized void updatePing(String id, long pingMs) {
        final List<Server> servers = getServers();
        boolean found = false;
        for (Server s : servers) {
            if (s.id.equals(id)) {
                s.lastPingMs = pingMs;
                found = true;
                break;
            }
        }
        if (found) {
            save(servers);
        }
    }

    /** The scheme off the front of the link (`vless`, `vmess`, `trojan`, `ss`) - what's actually
     *  supported for connecting is narrower than what can be stored and shown in the list. */
    public static String detectProtocol(String url) {
        if (url == null) {
            return "unknown";
        }
        final int idx = url.indexOf("://");
        return idx > 0 ? url.substring(0, idx).toLowerCase(Locale.ROOT) : "unknown";
    }

    public static boolean isSupportedShareLink(String url) {
        final String protocol = detectProtocol(url);
        return "vless".equals(protocol) || "vmess".equals(protocol) || "trojan".equals(protocol)
                || "ss".equals(protocol) || "socks".equals(protocol);
    }

    /**
     * Pull every vless/vmess/trojan/ss/socks URI out of a clipboard dump, a subscription body,
     * or a notes-app paste - extra prose, HTML, commas and wrapping quotes are ignored the way
     * Happ/v2rayNG ignore them.
     */
    public static List<String> extractShareLinks(String text) {
        final List<String> links = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return links;
        }
        final String decoded = maybeDecodeSubscriptionBody(text.trim());
        final Matcher m = SHARE_LINK.matcher(decoded);
        final LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String link = stripTrailingJunk(m.group());
            if (!isSupportedShareLink(link)) {
                continue;
            }
            final String key = normalizeUrlKey(link);
            if (seen.add(key)) {
                links.add(link);
            }
        }
        return links;
    }

    public static boolean looksLikeSubscriptionUrl(String text) {
        if (text == null) {
            return false;
        }
        final String t = text.trim();
        if (t.indexOf('\n') >= 0 || t.indexOf(' ') >= 0) {
            return false;
        }
        if (!t.startsWith("http://") && !t.startsWith("https://")) {
            return false;
        }
        return extractShareLinks(t).isEmpty();
    }

    /** Fragment after `#`, or vmess `ps`, URL-decoded. Empty if the generator didn't put a name. */
    public static String extractRemark(String url) {
        if (url == null) {
            return "";
        }
        try {
            if (url.toLowerCase(Locale.ROOT).startsWith("vmess://")) {
                final String payload = url.substring("vmess://".length());
                final int hash = payload.indexOf('#');
                final String b64 = hash >= 0 ? payload.substring(0, hash) : payload;
                byte[] decodedBytes;
                try {
                    decodedBytes = Base64.decode(b64, Base64.DEFAULT);
                } catch (Throwable t) {
                    decodedBytes = Base64.decode(b64, Base64.URL_SAFE | Base64.NO_WRAP);
                }
                final String json = new String(decodedBytes);
                final String ps = new JSONObject(json).optString("ps", "");
                if (!ps.trim().isEmpty()) {
                    return ps.trim();
                }
                if (hash >= 0) {
                    return java.net.URLDecoder.decode(payload.substring(hash + 1), "UTF-8").trim();
                }
                return "";
            }
            final int hash = url.lastIndexOf('#');
            if (hash < 0 || hash == url.length() - 1) {
                return "";
            }
            return java.net.URLDecoder.decode(url.substring(hash + 1), "UTF-8").replace('+', ' ').trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized Server addServer(String name, String rawUrl) {
        final List<Server> servers = getServers();
        final String protocol = detectProtocol(rawUrl);
        final String id = UUID.randomUUID().toString();
        String finalName = name != null ? name.trim() : "";
        if (finalName.isEmpty()) {
            finalName = extractRemark(rawUrl);
        }
        if (finalName.isEmpty()) {
            final vpn.sdk.HostPort hp = vpn.sdk.VpnSDK.extractHostPort(rawUrl);
            finalName = hp != null
                    ? (protocol.toUpperCase(Locale.ROOT) + " " + hp.host)
                    : (protocol.toUpperCase(Locale.ROOT) + " " + (servers.size() + 1));
        }
        final Server s = new Server(id, finalName, protocol, rawUrl.trim(), System.currentTimeMillis(), -1);
        servers.add(s);
        save(servers);
        return s;
    }

    /** @return how many new servers were stored (duplicates by host/uuid payload are skipped). */
    public static synchronized int addServersFromText(String nameHint, String text) {
        final List<String> links = extractShareLinks(text);
        if (links.isEmpty()) {
            return 0;
        }
        final List<Server> servers = getServers();
        final LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (Server s : servers) {
            existing.add(normalizeUrlKey(s.rawUrl));
        }
        int added = 0;
        final boolean singleNamed = links.size() == 1 && nameHint != null && !nameHint.trim().isEmpty();
        for (String link : links) {
            final String key = normalizeUrlKey(link);
            if (!existing.add(key)) {
                continue;
            }
            final String protocol = detectProtocol(link);
            final String id = UUID.randomUUID().toString();
            String name = singleNamed ? nameHint.trim() : extractRemark(link);
            if (name.isEmpty()) {
                final vpn.sdk.HostPort hp = vpn.sdk.VpnSDK.extractHostPort(link);
                name = hp != null
                        ? (protocol.toUpperCase(Locale.ROOT) + " " + hp.host)
                        : (protocol.toUpperCase(Locale.ROOT) + " " + (servers.size() + 1));
            }
            servers.add(new Server(id, name, protocol, link, System.currentTimeMillis(), -1));
            added++;
        }
        if (added > 0) {
            save(servers);
        }
        return added;
    }

    public static synchronized void removeServer(String id) {
        final List<Server> servers = getServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(id)) {
                servers.remove(i);
                break;
            }
        }
        save(servers);
        if (id.equals(getActiveServerId())) {
            setActiveServerId(null);
        }
    }

    public static String getActiveServerId() {
        return prefs().getString(KEY_ACTIVE, null);
    }

    public static void setActiveServerId(String id) {
        if (id == null) {
            prefs().edit().remove(KEY_ACTIVE).apply();
        } else {
            prefs().edit().putString(KEY_ACTIVE, id).apply();
        }
    }

    private static String normalizeUrlKey(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        final int hash = u.lastIndexOf('#');
        if (hash > 0) {
            u = u.substring(0, hash);
        }
        return u;
    }

    private static String stripTrailingJunk(String link) {
        int end = link.length();
        while (end > 0) {
            final char c = link.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == ')' || c == ']' || c == '}' || c == '"' || c == '\'') {
                end--;
            } else {
                break;
            }
        }
        return end == link.length() ? link : link.substring(0, end);
    }

    /**
     * Subscription endpoints often return one base64 blob of newline-separated share links
     * rather than the links themselves. If decoding yields more share schemes than the raw
     * body, use the decoded form; otherwise keep the original (already a list of URIs, or
     * unrelated text).
     */
    static String maybeDecodeSubscriptionBody(String body) {
        try {
            String compact = body.replaceAll("\\s+", "");
            if (compact.length() < 16) {
                return body;
            }
            final byte[] raw = Base64.decode(compact, Base64.DEFAULT);
            final String decoded = new String(raw, "UTF-8");
            if (SHARE_LINK.matcher(decoded).find()
                    && countMatches(decoded) >= countMatches(body)) {
                return decoded;
            }
        } catch (Throwable ignored) {
        }
        return body;
    }

    private static int countMatches(String text) {
        int n = 0;
        final Matcher m = SHARE_LINK.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
