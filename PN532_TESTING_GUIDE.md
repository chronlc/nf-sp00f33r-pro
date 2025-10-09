# PN532 Testing Setup Guide
**Date:** October 9, 2025  
**Hardware:** PN532 NFC Module + Android Phone + Physical NFC Cards

---

## 🔌 Hardware Connection Setup

### PN532 Bluetooth Connection (rfcomm0)

#### 1. Find PN532 MAC Address
```bash
# Scan for Bluetooth devices
hcitool scan

# Expected output:
# Scanning ...
#     00:11:22:33:44:55    PN532
```

#### 2. Bind PN532 to rfcomm0
```bash
# Bind to rfcomm0 (replace MAC address)
sudo rfcomm bind 0 00:11:22:33:44:55

# Verify binding
ls -l /dev/rfcomm0
# Output: crw-rw---- 1 root dialout ... /dev/rfcomm0

# Fix permissions (if needed)
sudo chmod 666 /dev/rfcomm0
```

#### 3. Check Connection
```bash
# Test serial port
python3 -c "import serial; s = serial.Serial('/dev/rfcomm0', 115200, timeout=1); print('✅ Port accessible')"
```

---

## 🧪 Test Scenarios

### Scenario 1: Phone Reads Physical NFC Card
**Setup:** Phone → NFC Card  
**Goal:** Test Android internal NFC reading

```
┌─────────────┐
│   Phone     │  (NFC enabled)
└──────┬──────┘
       │ (on top of)
   ┌───▼────┐
   │ NFC    │  (Physical EMV/Mifare card)
   │ Card   │
   └────────┘
```

**Steps:**
1. Place NFC card on flat surface
2. Open nf-sp00f33r app → CardReading screen
3. Place phone on top of card
4. App should detect and read card via Android NFC

**Python Command:** (informational only)
```bash
python3 scripts/pn532_controller.py --mode phone-reads-card
```

---

### Scenario 2: PN532 Reads Physical NFC Card
**Setup:** Card → PN532 → Bluetooth → VSCode Terminal  
**Goal:** Control PN532 via Python to read card

```
   ┌────────┐
   │ NFC    │  (Physical EMV/Mifare card)
   │ Card   │
   └───┬────┘
       │ (on top of)
┌──────▼──────┐
│   PN532     │  (Bluetooth module)
│   Module    │
└──────┬──────┘
       │ (rfcomm0)
   ┌───▼──────────┐
   │  VSCode      │  (Python script control)
   │  Terminal    │
   └──────────────┘
```

**Python Command:**
```bash
# Connect and read card
python3 scripts/pn532_controller.py --port /dev/rfcomm0 --mode pn532-reads-card
```

**Expected Output:**
```
🔧 PN532 Controller initialized
   Port: /dev/rfcomm0
   Baudrate: 115200
   Timeout: 2s

📡 Connecting to PN532 on /dev/rfcomm0...
✅ Connected to PN532
   Firmware: v1.6 (IC: 0x32, Support: 0x01)

================================================================
🔧 SCENARIO 2: PN532 Reads Physical NFC Card
================================================================

🔄 Polling for cards... (Ctrl+C to stop)

✅ Card detected!
   UID: 08123456
   Type: ISO14443-4 (EMV/Smart Card)
   SENS_RES: 0004
   SEL_RES: 20

💬 Transceiving APDU:
   Command: 00A404000E325041592E5359532E444446303100
   Response: 6F2A840E325041592E5359532E4444463031A518...9000
   Status Word: 9000

✅ SELECT PPSE successful
✅ Card reading complete
```

---

### Scenario 3: PN532 Emulates Card (Phone Reads PN532)
**Setup:** Phone → PN532 (emulating card) → VSCode Terminal  
**Goal:** PN532 emulates NFC card, phone reads it

```
┌─────────────┐
│   Phone     │  (NFC enabled)
└──────┬──────┘
       │ (on top of)
┌──────▼──────┐
│   PN532     │  (Emulating NFC card)
│   Module    │
└──────┬──────┘
       │ (rfcomm0)
   ┌───▼──────────┐
   │  VSCode      │  (Python script receives APDUs)
   │  Terminal    │
   └──────────────┘
```

**Python Command:**
```bash
# Start card emulation mode
python3 scripts/pn532_controller.py --port /dev/rfcomm0 --mode pn532-emulates
```

**Expected Output:**
```
🔧 PN532 Controller initialized
   Port: /dev/rfcomm0

📡 Connecting to PN532...
✅ Connected to PN532
   Firmware: v1.6

================================================================
🎭 SCENARIO 3: PN532 Emulates Card (Phone Reads PN532)
================================================================

🎭 Initializing card emulation mode...
✅ Card emulation mode active
   Phone can now read this PN532 as a card

🔄 Waiting for phone to connect... (Ctrl+C to stop)

📱 Phone sent command: 00A404000E325041592E5359532E444446303100
   CLA: 00
   INS: A4  (SELECT)
   P1: 04
   P2: 00
   ↳ Responded: 9000

📱 Phone sent command: 00B2010C00
   CLA: 00
   INS: B2  (READ RECORD)
   P1: 01
   P2: 0C
   ↳ Responded: 9000
```

---

## 🐍 Python Script Features

### PN532Controller Class

**Core Methods:**
```python
controller = PN532Controller(port='/dev/rfcomm0', baudrate=115200)

# Connection
controller.connect()                    # Connect to PN532
controller.disconnect()                 # Disconnect
controller.get_firmware_version()       # Get firmware info

# Card Reading
controller.sam_configuration()          # Configure SAM
card = controller.read_passive_target() # Detect card
response = controller.transceive_apdu(  # Send APDU
    target=1, 
    apdu=b'\x00\xA4...'
)

# Card Emulation
controller.init_as_target()            # Enter emulation mode
data = controller.get_target_data()    # Get data from phone
controller.set_target_data(data)       # Send data to phone
```

**Card Detection Response:**
```python
{
    'target_number': 1,
    'sens_res': '0004',
    'sel_res': '20',
    'uid_length': 4,
    'uid': '08123456',
    'card_type': 'ISO14443-4 (EMV/Smart Card)'
}
```

---

## 🔍 Debugging & Troubleshooting

### Check rfcomm0 Binding
```bash
# List rfcomm devices
rfcomm -a

# Expected output:
# rfcomm0: 00:11:22:33:44:55 channel 1 clean

# If not bound, bind it
sudo rfcomm bind 0 <MAC_ADDRESS>
```

### Test Serial Communication
```bash
# Send AT commands (if PN532 supports)
echo "AT" > /dev/rfcomm0

# Monitor serial traffic
sudo cat /dev/rfcomm0 | hexdump -C
```

### Python Debug Mode
```bash
# Enable verbose debug output
python3 scripts/pn532_controller.py --mode pn532-reads-card --debug
```

### Common Issues

**Issue 1: Permission Denied**
```bash
# Fix: Add user to dialout group
sudo usermod -a -G dialout $USER

# Or: Fix permissions directly
sudo chmod 666 /dev/rfcomm0
```

**Issue 2: Device Busy**
```bash
# Fix: Release rfcomm0
sudo rfcomm release 0

# Rebind
sudo rfcomm bind 0 <MAC_ADDRESS>
```

**Issue 3: No Response from PN532**
```bash
# Check Bluetooth connection
hcitool con

# Reset Bluetooth
sudo systemctl restart bluetooth

# Reconnect PN532
sudo rfcomm bind 0 <MAC_ADDRESS>
```

**Issue 4: Card Not Detected**
- Check card is ISO14443A compatible
- Ensure card is centered on PN532 antenna
- Try different cards (Mifare, EMV)
- Check PN532 LED indicators

---

## 📋 EMV Command Testing

### Common EMV APDUs

**SELECT PPSE:**
```python
select_ppse = bytes.fromhex('00A404000E325041592E5359532E444446303100')
response = controller.transceive_apdu(target, select_ppse)
```

**SELECT Application:**
```python
# Visa AID: A0000000031010
select_app = bytes.fromhex('00A4040007A0000000031010')
response = controller.transceive_apdu(target, select_app)
```

**GET PROCESSING OPTIONS:**
```python
gpo = bytes.fromhex('80A8000002830000')
response = controller.transceive_apdu(target, gpo)
```

**READ RECORD:**
```python
read_record = bytes.fromhex('00B2011400')
response = controller.transceive_apdu(target, read_record)
```

---

## 🎯 Integration with nf-sp00f33r App

### Scenario 1: App Uses Internal NFC
- App's CardReading screen uses Android NFC API
- Phone reads card directly (no PN532 involved)
- Best for: Testing app's NFC reading functionality

### Scenario 2: App Connects to PN532
- Modify FuzzingEngine to use PN532 via Bluetooth
- NfcFuzzingExecutor already supports PN532_HARDWARE mode
- Python script can log all APDU traffic

**Future Enhancement:**
```kotlin
// In NfcFuzzingExecutor.kt
val pn532 = PN532DeviceModule()
pn532.connectBluetooth("/dev/rfcomm0")
val response = pn532.transceive(apdu)
```

### Scenario 3: Automated Testing
- Python script emulates card
- App reads emulated card
- Python logs all APDUs for analysis
- Perfect for fuzzing validation

---

## 🚀 Quick Start Commands

### Basic Connection Test
```bash
python3 scripts/pn532_controller.py --port /dev/rfcomm0
```

### Read Card Test
```bash
# Place card on PN532, then run:
python3 scripts/pn532_controller.py --mode pn532-reads-card
```

### Emulation Test
```bash
# Start emulation, then place phone on PN532:
python3 scripts/pn532_controller.py --mode pn532-emulates
```

### Custom Script
```python
from pn532_controller import PN532Controller

controller = PN532Controller('/dev/rfcomm0')
controller.connect()

# Your custom testing code here
card = controller.read_passive_target()
if card:
    print(f"Card UID: {card['uid']}")

controller.disconnect()
```

---

## 📊 Expected Test Results

### Successful Card Read:
```
✅ Card detected!
   UID: 08123456ABCDEF01
   Type: ISO14443-4 (EMV/Smart Card)
   SENS_RES: 0004
   SEL_RES: 20
```

### Successful APDU Exchange:
```
💬 Transceiving APDU:
   Command: 00A404000E325041592E5359532E444446303100
   Response: 6F2A840E325041592E5359532E4444463031A5189000
   Status Word: 9000
```

### Successful Emulation:
```
🎭 Card emulation mode active
📱 Phone sent command: 00A404000E325041592E5359532E444446303100
   ↳ Responded: 9000
```

---

## 🎉 Ready to Test!

1. **Connect PN532:** `sudo rfcomm bind 0 <MAC>`
2. **Run Script:** `python3 scripts/pn532_controller.py --mode pn532-reads-card`
3. **Place Card:** Put NFC card on PN532
4. **Watch Output:** See card detection and APDU exchanges in terminal

**All test scenarios supported! 🚀**
