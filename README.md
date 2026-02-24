
# Proxyman Log Importer (Burp Suite Extension)

A working Burp Suite extension that imports Proxyman `.proxymanlogv2` files.

Features:
- Request list with filter/search
- A dedicated tab per request (request + response viewers)
- Per-request actions: Send to Repeater, Add to Site Map
- Bulk actions: Send ALL to Repeater, Add ALL to Site Map
- Handles Proxyman schema variations (version/status can be objects)
- Normalizes absolute URLs into origin-form paths

## Build
```bash
chmod +x ./gradlew
./gradlew jar
```

Output:
- `build/libs/proxyman-log-importer-1.0.0.jar`

## Load into Burp
Burp → Extensions → Installed → Add → Java
Select the JAR above.
