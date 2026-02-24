
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import com.google.gson.Gson;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProxymanParser {

    private static final Gson GSON = new Gson();

    public static class ParsedEntry {
        public final String host;
        public final int port;
        public final boolean secure;
        public final String method;
        public final String path;
        public final HttpRequest request;
        public final HttpResponse response;

        public ParsedEntry(String host, int port, boolean secure, String method, String path,
                           HttpRequest request, HttpResponse response) {
            this.host = host;
            this.port = port;
            this.secure = secure;
            this.method = method;
            this.path = path;
            this.request = request;
            this.response = response;
        }
    }

    public static List<ParsedEntry> parse(File file) throws Exception {
        if (file == null || !file.exists()) throw new IllegalArgumentException("File not found.");

        List<ParsedEntry> results = new ArrayList<>();

        try (ZipFile zip = new ZipFile(file)) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            entries.sort(Comparator.comparing(ProxymanParser::sortKey));

            for (ZipEntry ze : entries) {
                if (ze.isDirectory()) continue;
                if (!ze.getName().startsWith("request_")) continue;

                try (InputStream is = zip.getInputStream(ze)) {
                    String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    ProxymanModels.LogEntry entry = GSON.fromJson(json, ProxymanModels.LogEntry.class);
                    if (entry == null || entry.request == null) continue;
                    ParsedEntry pe = toParsed(entry);
                    if (pe != null) results.add(pe);
                }
            }
        }

        return results;
    }

    private static ParsedEntry toParsed(ProxymanModels.LogEntry e) {
        String host = safe(e.request.host);
        boolean secure = Boolean.TRUE.equals(e.request.isSSL);
        int port = e.request.port != null ? e.request.port : (secure ? 443 : 80);

        String method = parseStringLike(e.request.method);
        if (method.isEmpty()) method = "GET";

        String path = normalizePath(safe(e.request.fullPath));
        if (path.isEmpty()) path = "/";

        String version = parseHttpVersion(e.request.version);
        if (version.isEmpty()) version = "HTTP/1.1";

        String startLine = method + " " + path + " " + version + "\r\n";
        String headers = buildHeaders(e.request.header, host, port, secure, true);
        byte[] body = decodeBase64(e.request.bodyData);

        if (body.length > 0 && !headerBlockContains(e.request.header, "Content-Length")) {
            headers = headers + "Content-Length: " + body.length + "\r\n";
        }

        byte[] requestBytes = concat(
                startLine.getBytes(StandardCharsets.UTF_8),
                headers.getBytes(StandardCharsets.UTF_8),
                "\r\n".getBytes(StandardCharsets.UTF_8),
                body
        );

        HttpService service = HttpService.httpService(host, port, secure);
        HttpRequest request = HttpRequest.httpRequest(service, ByteArray.byteArray(requestBytes));

        HttpResponse response;
        if (e.response != null) {
            String respVersion = parseHttpVersion(e.response.version);
            if (respVersion.isEmpty()) respVersion = "HTTP/1.1";

            StatusLine sl = parseStatus(e.response.status);
            int status = sl.code;
            String reason = sl.phrase != null ? sl.phrase : defaultReasonPhrase(status);

            String statusLine = respVersion + " " + status + (reason.isEmpty() ? "" : (" " + reason)) + "\r\n";

            String respHeaders = buildHeaders(e.response.header, host, port, secure, false);
            byte[] respBody = decodeBase64(e.response.bodyData);

            if (respBody.length > 0 && !headerBlockContains(e.response.header, "Content-Length")) {
                respHeaders = respHeaders + "Content-Length: " + respBody.length + "\r\n";
            }

            byte[] responseBytes = concat(
                    statusLine.getBytes(StandardCharsets.UTF_8),
                    respHeaders.getBytes(StandardCharsets.UTF_8),
                    "\r\n".getBytes(StandardCharsets.UTF_8),
                    respBody
            );

            response = HttpResponse.httpResponse(ByteArray.byteArray(responseBytes));
        } else {
            response = HttpResponse.httpResponse("HTTP/1.1 0\r\n\r\n");
        }

        return new ParsedEntry(host, port, secure, method, path, request, response);
    }

    private static String normalizePath(String fullPath) {
        if (fullPath == null) return "";
        String s = fullPath.trim();
        if (s.isEmpty()) return "";

        if (s.startsWith("http://") || s.startsWith("https://")) {
            try {
                URI u = URI.create(s);
                String p = u.getRawPath();
                if (p == null || p.isEmpty()) p = "/";
                String q = u.getRawQuery();
                if (q != null && !q.isEmpty()) p = p + "?" + q;
                return p;
            } catch (Exception ignored) {}
        }

        return s;
    }

    private static class StatusLine {
        final int code;
        final String phrase;
        StatusLine(int code, String phrase) { this.code = code; this.phrase = phrase; }
    }

    private static StatusLine parseStatus(Object statusObj) {
        if (statusObj == null) return new StatusLine(0, "");
        if (statusObj instanceof Number n) return new StatusLine(n.intValue(), "");
        if (statusObj instanceof String s) {
            try { return new StatusLine(Integer.parseInt(s.trim()), ""); }
            catch (Exception ignored) { return new StatusLine(0, ""); }
        }
        if (statusObj instanceof Map<?,?> map) {
            int code = 0;
            String phrase = "";
            Object c = map.get("code");
            if (c instanceof Number n) code = n.intValue();
            else if (c instanceof String cs) {
                try { code = Integer.parseInt(cs.trim()); } catch (Exception ignored) {}
            }
            Object p = map.get("phrase");
            if (p instanceof String ps) phrase = ps;
            return new StatusLine(code, phrase);
        }
        return new StatusLine(0, "");
    }

    private static String parseHttpVersion(Object obj) {
        if (obj == null) return "";

        if (obj instanceof String s) {
            s = s.trim();
            if (s.isEmpty()) return "";
            if (!s.toUpperCase(Locale.ROOT).startsWith("HTTP/") && s.matches("\\d+(\\.\\d+)?")) {
                return "HTTP/" + s;
            }
            return s;
        }

        if (obj instanceof Map<?,?> map) {
            Object name = map.get("name");
            if (name instanceof String ns && !ns.trim().isEmpty()) return ns.trim();

            Object major = map.get("major");
            Object minor = map.get("minor");
            Integer maj = toInt(major);
            Integer min = toInt(minor);
            if (maj != null && min != null) {
                return "HTTP/" + maj + "." + min;
            }

            Object value = map.get("value");
            if (value instanceof String vs && !vs.trim().isEmpty()) return vs.trim();
        }

        return obj.toString();
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                double d = Double.parseDouble(s.trim());
                return (int) d;
            } catch (Exception ignored) { return null; }
        }
        return null;
    }

    private static String parseStringLike(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String s) return s;

        if (obj instanceof Map<?,?> map) {
            Object v = map.get("name");
            if (v instanceof String) return (String)v;
            v = map.get("value");
            if (v instanceof String) return (String)v;
            v = map.get("rawValue");
            if (v instanceof String) return (String)v;
            v = map.get("version");
            if (v instanceof String) return (String)v;
        }

        return obj.toString();
    }

    private static String buildHeaders(ProxymanModels.HeaderBlock hb, String host, int port, boolean secure, boolean isRequest) {
        StringBuilder sb = new StringBuilder();
        boolean hasHost = false;

        if (hb != null && hb.entries != null) {
            for (ProxymanModels.HeaderEntry he : hb.entries) {
                if (he == null || he.key == null) continue;
                if (he.isEnabled != null && !he.isEnabled) continue;

                String name = safe(he.key.name);
                String value = tryDecode(he.value);

                if (name.equalsIgnoreCase("host")) hasHost = true;

                if (!name.isEmpty()) {
                    sb.append(name).append(": ").append(value).append("\r\n");
                }
            }
        }

        if (isRequest && !hasHost && host != null && !host.isEmpty()) {
            boolean nonDefaultPort = (secure && port != 443) || (!secure && port != 80);
            sb.append("Host: ").append(host);
            if (nonDefaultPort) sb.append(":").append(port);
            sb.append("\r\n");
        }

        return sb.toString();
    }

    private static boolean headerBlockContains(ProxymanModels.HeaderBlock hb, String headerName) {
        if (hb == null || hb.entries == null) return false;
        for (ProxymanModels.HeaderEntry he : hb.entries) {
            if (he == null || he.key == null) continue;
            String name = safe(he.key.name);
            if (name.equalsIgnoreCase(headerName)) return true;
        }
        return false;
    }

    private static byte[] decodeBase64(String b64) {
        if (b64 == null || b64.isEmpty()) return new byte[0];
        try {
            int mod = b64.length() % 4;
            if (mod != 0) b64 = b64 + "====".substring(mod);
            return Base64.getDecoder().decode(b64);
        } catch (Exception ex) {
            return new byte[0];
        }
    }

    private static String tryDecode(String s) {
        if (s == null) return "";
        if (!s.contains("%")) return s;
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception ignored) { return s; }
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += (p != null ? p.length : 0);
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            if (p == null) continue;
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String sortKey(ZipEntry ze) {
        String n = ze.getName();
        int idx = n.indexOf('_');
        if (idx >= 0 && idx + 1 < n.length()) {
            String tail = n.substring(idx + 1);
            try {
                int v = Integer.parseInt(tail.trim());
                return n.substring(0, idx + 1) + String.format("%06d", v);
            } catch (Exception ignored) {}
        }
        return n;
    }

    private static String defaultReasonPhrase(int status) {
        return switch (status) {
            case 100 -> "Continue";
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "";
        };
    }
}
