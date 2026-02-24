
import java.util.List;

public class ProxymanModels {

    public static class LogEntry {
        public Request request;
        public Response response;
    }

    public static class Request {
        public Object method;
        public String fullPath;
        public Object version;  // can be string or object (major/minor or name)
        public String scheme;
        public String host;
        public Integer port;
        public Boolean isSSL;
        public HeaderBlock header;
        public String bodyData;
    }

    public static class Response {
        public Object status;   // can be int OR object like {"code":500,"phrase":"Internal Server Error"}
        public Object version;  // can be string or object
        public HeaderBlock header;
        public String bodyData;
        public Object error;
    }

    public static class HeaderBlock {
        public List<HeaderEntry> entries;
    }

    public static class HeaderEntry {
        public HeaderKey key;
        public String value;
        public Boolean isEnabled;
    }

    public static class HeaderKey {
        public String name;
        public String nameInLowercase;
    }
}
