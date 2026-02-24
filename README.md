# Proxyman Log Importer

A Burp Suite extension for importing Proxyman `.proxymanlogv2` logs into Burp with full request/response reconstruction.

---

## Overview

**Proxyman Log Importer** allows you to take exported Proxyman session logs and load them directly into Burp Suite for analysis.

Each imported entry is reconstructed as a proper Burp `HttpRequestResponse` object and can be:

- Viewed in a structured request list
- Opened in a dedicated request/response tab
- Sent to Repeater
- Added to the Site Map
- Filtered and searched

This makes it easy to transition mobile/API traffic captured in Proxyman into a full Burp testing workflow.

---

## Features

### Import Proxyman Logs

- Supports `.proxymanlogv2` files  
- Parses zipped Proxyman log format  
- Automatically sorts requests in proper sequence  

### Accurate HTTP Reconstruction

- Converts absolute URLs into proper origin-form requests  
- Normalizes HTTP version fields (handles object formats like `{major:1, minor:1}`)  
- Handles response status as:
  - Integer
  - String
  - Object (`{code: 200, phrase: "OK"}`)
- Preserves request/response bodies  
- Automatically sets `Content-Length` if missing  

### Burp Integration

- Add individual requests to:
  - Repeater
  - Site Map
- Bulk actions:
  - Send ALL to Repeater
  - Add ALL to Site Map

### Modern UI

- Clean, production-ready layout  
- Sortable request table  
- Filter/search box  
- Dedicated request detail tabs  
- Closable tabs  
- Progress indicator while loading  
- Optional: auto-open all request tabs  

---

## Why Add to Site Map (Not Scope)?

The extension adds imported traffic to Burp’s **Site Map**, not Scope.

This is intentional:

- Does not modify user scope unexpectedly  
- Does not trigger scanning  
- Does not alter interception rules  
- Preserves imported traffic safely for review  

Scope modification is intentionally left to the tester.

---

## Installation

### 1. Build the Extension

From the project root:

```
chmod +x ./gradlew
./gradlew jar
```

The compiled JAR will be located at:
```
build/libs/proxyman-log-importer-1.0.0.jar
```

### 2. Load into Burp

```
Open Burp Suite
Go to Extensions → Installed
Click Add
Select:
Extension type: Java
File: proxyman-log-importer-1.0.0.jar
```

You will now see a new tab:
```
Proxyman Log Importer
```

## Usage

```
Open the Proxyman Log Importer tab
Click Load .proxymanlogv2
Select your exported Proxyman log
Review imported traffic in the request table
Select a request to open its detail tab
```


## Technical Details

- Built using Burp’s Montoya API
- Uses Gson for JSON parsing
- Supports large logs with background loading
- UI built with Swing (theme-aware via Burp UI API)

## Use Cases

- Import mobile API traffic for deeper testing
- Replay captured requests in Repeater
- Analyze production traffic safely
- Migrate testing workflow from Proxyman to Burp
- Share captured logs across teams using Burp

## Limitations

- Does not automatically modify Scope
- Does not automatically trigger scanning
- Import-only (no live synchronization)
