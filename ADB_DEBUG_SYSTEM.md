# ADB Debug System - AI Agent Command Interface

## Overview

The ADB Debug System provides a powerful command interface for AI agents and developers to inspect and control the nf-sp00f33r application remotely via ADB (Android Debug Bridge).

**Security:** Debug commands are only available in debug builds (`BuildConfig.DEBUG`). Release builds ignore all debug commands.

## Architecture

- **DebugCommandReceiver.kt**: Broadcast receiver that accepts ADB commands
- **DebugCommandProcessor.kt**: Command execution engine with JSON responses
- **AndroidManifest.xml**: Receiver registration with intent filter

## Basic Usage

```bash
# General command format
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "<command_name>" \
  --es params '{"param1":"value1","param2":"value2"}'

# View results in logcat
adb logcat -s "🔧 DebugCmd"
```

## Available Commands

### 1. LOGCAT - Filter Application Logs

Retrieve filtered application logs with specific tag and level.

**Parameters:**
- `filter` (string): Log tag filter (default: "NfSp00f")
- `level` (string): Log level V/D/I/W/E (default: "D")
- `lines` (int): Number of lines to retrieve (default: 50)

**Example:**
```bash
# Get last 50 debug logs
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "logcat" \
  --es params '{"filter":"NfSp00f","level":"D","lines":50}'

# Get error logs only
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "logcat" \
  --es params '{"filter":"NfSp00f","level":"E","lines":100}'
```

**Response:**
```json
{
  "status": "success",
  "command": "logcat",
  "timestamp": 1696867200000,
  "filter": "NfSp00f",
  "level": "D",
  "count": 50,
  "logs": [
    "10-09 04:50:00.123 D/NfSp00f: Module initialized",
    "10-09 04:50:01.234 D/NfSp00f: CardDataStore loaded 30 profiles"
  ]
}
```

### 2. DB - Database Inspection

Query encrypted card storage with count, list, or get operations.

**Parameters:**
- `query` (string): "count", "list", or "get" (default: "count")
- `limit` (int): Number of records for list (default: 10)
- `id` (string): Card ID for get operation

**Examples:**
```bash
# Count total cards
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "db" \
  --es params '{"query":"count"}'

# List first 10 cards
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "db" \
  --es params '{"query":"list","limit":10}'

# Get specific card by ID
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "db" \
  --es params '{"query":"get","id":"12345678-abcd-ef01-2345-67890abcdef0"}'
```

**Response (count):**
```json
{
  "status": "success",
  "command": "db",
  "timestamp": 1696867200000,
  "query": "count",
  "total_cards": 30,
  "encrypted": true
}
```

**Response (list):**
```json
{
  "status": "success",
  "command": "db",
  "timestamp": 1696867200000,
  "query": "list",
  "count": 10,
  "cards": [
    {
      "id": "12345678-abcd-ef01-2345-67890abcdef0",
      "pan": "1234",
      "cardholder": "JOHN DOE",
      "created": "2025-10-09 04:50:00"
    }
  ]
}
```

### 3. STATE - Module Health & State Validation

Get current state of all registered modules (6 modules).

**Parameters:** None

**Example:**
```bash
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "state"
```

**Response:**
```json
{
  "status": "success",
  "command": "state",
  "timestamp": 1696867200000,
  "total_modules": 6,
  "modules": [
    {
      "name": "LoggingModule",
      "state": "RUNNING",
      "healthy": true
    },
    {
      "name": "SecureMasterPasswordModule",
      "state": "RUNNING",
      "healthy": true
    },
    {
      "name": "CardDataStoreModule",
      "state": "RUNNING",
      "healthy": true,
      "card_count": 30
    },
    {
      "name": "PN532DeviceModule",
      "state": "RUNNING",
      "healthy": true,
      "connected": false
    },
    {
      "name": "NfcHceModule",
      "state": "RUNNING",
      "healthy": true
    },
    {
      "name": "EmulationModule",
      "state": "RUNNING",
      "healthy": true
    }
  ]
}
```

### 4. HEALTH - Real-Time Module Metrics

Get detailed health metrics for hardware, storage, and emulation systems.

**Parameters:** None

**Example:**
```bash
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "health"
```

**Response:**
```json
{
  "status": "success",
  "command": "health",
  "timestamp": 1696867200000,
  "metrics": [
    {
      "component": "PN532 Hardware",
      "status": "Disconnected",
      "healthy": true,
      "state": "RUNNING"
    },
    {
      "component": "Card Storage",
      "status": "Operational",
      "healthy": true,
      "card_count": 30,
      "encrypted": true
    },
    {
      "component": "Emulation System",
      "status": "RUNNING",
      "healthy": true
    }
  ]
}
```

### 5. APDU - APDU Log Inspection

Retrieve APDU command/response logs from stored cards.

**Parameters:**
- `card_id` (string): Card profile ID (optional, shows all if not specified)
- `limit` (int): Number of APDUs per card (default: 20)

**Examples:**
```bash
# Get APDUs from all cards (max 5 cards)
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "apdu" \
  --es params '{"limit":20}'

# Get APDUs from specific card
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "apdu" \
  --es params '{"card_id":"12345678-abcd-ef01-2345-67890abcdef0","limit":50}'
```

**Response:**
```json
{
  "status": "success",
  "command": "apdu",
  "timestamp": 1696867200000,
  "cards_inspected": 5,
  "apdu_count": 85,
  "apdus": [
    {
      "card_id": "12345678-abcd-ef01-2345-67890abcdef0",
      "timestamp": "2025-10-09 04:50:12",
      "command": "00A404000E325041592E5359532E4444463031",
      "response": "6F3C840E325041592E5359532E4444463031A52A...",
      "status": "9000",
      "description": "SELECT PPSE",
      "execution_ms": 45
    }
  ]
}
```

### 6. ROCA - ROCA Vulnerability Scan Results

Get information about ROCA vulnerability scanning capabilities.

**Parameters:** None

**Example:**
```bash
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "roca"
```

**Response:**
```json
{
  "status": "success",
  "command": "roca",
  "timestamp": 1696867200000,
  "message": "ROCA scanning available in Database screen",
  "trigger": "Use Database screen Security button to run batch scan",
  "features": [
    "Batch scanning with priority classification",
    "CRITICAL/HIGH/MEDIUM vulnerability levels",
    "512/1024/2048-bit key analysis",
    "Real-time badge display"
  ]
}
```

### 7. INTENT - Broadcast Custom Intents

Send custom broadcast intents for testing application behavior.

**Parameters:**
- `action` (string): Intent action (required)
- `extras` (object): Key-value pairs for intent extras

**Example:**
```bash
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "intent" \
  --es params '{"action":"com.nfsp00f33r.app.TEST_ACTION","extras":{"key1":"value1","key2":"value2"}}'
```

**Response:**
```json
{
  "status": "success",
  "command": "intent",
  "timestamp": 1696867200000,
  "action": "com.nfsp00f33r.app.TEST_ACTION",
  "extras": {
    "key1": "value1",
    "key2": "value2"
  }
}
```

### 8. HELP - Show Available Commands

Display all available commands with examples.

**Parameters:** None

**Example:**
```bash
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
  --es command "help"
```

## Monitoring Output

### Watch for Command Results
```bash
# Clear logcat and monitor debug commands
adb logcat -c && adb logcat -s "🔧 DebugCmd"

# Filter for JSON results only
adb logcat -s "🔧 DebugCmd" | grep "Result:"
```

### Parse JSON Output
```bash
# Extract JSON and pretty-print (requires jq)
adb logcat -s "🔧 DebugCmd" | grep "Result:" | sed 's/.*Result: //' | jq '.'
```

## AI Agent Integration

### Example: Automated Card Count Check
```python
import subprocess
import json
import re

def execute_debug_command(command, params=None):
    """Execute ADB debug command and return JSON result"""
    params_str = json.dumps(params) if params else "{}"
    
    # Send command
    subprocess.run([
        "adb", "shell", "am", "broadcast",
        "-a", "com.nfsp00f33r.app.DEBUG_COMMAND",
        "--es", "command", command,
        "--es", "params", params_str
    ])
    
    # Wait and capture logcat
    result = subprocess.run(
        ["adb", "logcat", "-d", "-s", "🔧 DebugCmd"],
        capture_output=True,
        text=True
    )
    
    # Parse JSON from logcat
    for line in result.stdout.split("\n"):
        if "Result:" in line:
            json_str = line.split("Result: ")[1]
            return json.loads(json_str)
    
    return None

# Check card count
result = execute_debug_command("db", {"query": "count"})
print(f"Total cards: {result['total_cards']}")

# Check module health
result = execute_debug_command("state")
print(f"Modules: {result['total_modules']}")
```

### Example: Monitor PN532 Connection
```bash
#!/bin/bash
# monitor_pn532.sh - Check PN532 connection status every 5 seconds

while true; do
    echo "Checking PN532 status..."
    adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command "health" > /dev/null
    sleep 1
    adb logcat -d -s "🔧 DebugCmd" | grep "PN532 Hardware" | tail -1
    sleep 5
done
```

## Security Considerations

1. **Debug Builds Only**: All commands are disabled in release builds
2. **ADB Access Required**: Commands only work with USB debugging enabled
3. **Local Network**: ADB over network should be secured
4. **Sensitive Data**: Card PANs are masked in list responses (last 4 digits only)
5. **Encrypted Storage**: Database operations access encrypted data via CardDataStore

## Troubleshooting

### Command Not Responding
```bash
# Check if receiver is registered
adb shell dumpsys package com.nfsp00f33r.app | grep "DebugCommandReceiver"

# Verify app is in debug build
adb logcat -s "🔧 DebugCmd" | grep "Debug commands disabled"
```

### No Output in Logcat
```bash
# Clear logcat buffer first
adb logcat -c

# Send command
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command "help"

# Check all logs
adb logcat -d | grep -i debug
```

### ADB Device Not Found
```bash
# List connected devices
adb devices

# Restart ADB server
adb kill-server && adb start-server

# Check USB debugging is enabled on device
```

## Use Cases

### Development
- Monitor module health during testing
- Inspect card storage without UI
- Debug APDU communication flows
- Validate encryption is working

### CI/CD Integration
- Automated health checks after deployment
- Card count validation in test suites
- Module state verification
- Log collection for failure analysis

### AI Agent Operations
- Query system state before operations
- Retrieve card data for analysis
- Monitor ROCA scan results
- Access APDU logs for pattern recognition

## Response Format

All commands return JSON with consistent structure:

**Success Response:**
```json
{
  "status": "success",
  "command": "<command_name>",
  "timestamp": <unix_ms>,
  "<command_specific_data>": "..."
}
```

**Error Response:**
```json
{
  "status": "error",
  "error": "<error_message>",
  "timestamp": <unix_ms>
}
```

## Version History

- **v1.0** (2025-10-09): Initial release with 8 commands
  - logcat, db, state, health, apdu, roca, intent, help
  - JSON response format
  - Debug build security
  - Module system integration
