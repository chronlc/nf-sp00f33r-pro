# PN532 Testing Quick Reference
**VSCode Terminal Control via rfcomm0**

---

## ⚡ Quick Setup (5 steps)

```bash
# 1. Install dependencies
./setup_pn532_testing.sh

# 2. Find PN532 MAC address
hcitool scan
# Output: 00:11:22:33:44:55    PN532

# 3. Bind to rfcomm0
sudo rfcomm bind 0 00:11:22:33:44:55

# 4. Fix permissions
sudo chmod 666 /dev/rfcomm0

# 5. Test connection
python3 scripts/pn532_controller.py
```

---

## 🎯 3 Test Scenarios

### 1️⃣ Phone Reads NFC Card (Android NFC)
```
📱 Phone
   ↓ (on top of)
💳 NFC Card
```
**Command:** Informational only (no PN532 involved)
```bash
python3 scripts/pn532_controller.py --mode phone-reads-card
```

---

### 2️⃣ PN532 Reads NFC Card (Bluetooth Control)
```
💳 NFC Card
   ↓ (on top of)
📡 PN532 Module
   ↓ (Bluetooth rfcomm0)
💻 VSCode Terminal
```
**Command:**
```bash
python3 scripts/pn532_controller.py --mode pn532-reads-card
```

**Output:**
```
✅ Card detected!
   UID: 08123456ABCDEF01
   Type: ISO14443-4 (EMV/Smart Card)

💬 Transceiving APDU:
   Command: 00A404000E325041592E5359532E444446303100
   Response: 6F2A840E...9000
   Status Word: 9000
```

---

### 3️⃣ PN532 Emulates Card (Phone Reads PN532)
```
📱 Phone (NFC enabled)
   ↓ (on top of)
📡 PN532 Module (emulating card)
   ↓ (Bluetooth rfcomm0)
💻 VSCode Terminal (logs APDUs)
```
**Command:**
```bash
python3 scripts/pn532_controller.py --mode pn532-emulates
```

**Output:**
```
🎭 Card emulation mode active
   Phone can now read this PN532 as a card

📱 Phone sent command: 00A404000E325041592E5359532E444446303100
   CLA: 00  INS: A4  P1: 04  P2: 00
   ↳ Responded: 9000
```

---

## 🔧 Common Commands

### Check Connection
```bash
# List Bluetooth devices
hcitool scan

# Check rfcomm binding
rfcomm -a

# Test serial port
ls -l /dev/rfcomm0
```

### Fix Issues
```bash
# Permission denied
sudo chmod 666 /dev/rfcomm0

# Device busy
sudo rfcomm release 0
sudo rfcomm bind 0 <MAC>

# Bluetooth issues
sudo systemctl restart bluetooth
```

---

## 📋 EMV APDU Examples

### Custom Python Script
```python
from scripts.pn532_controller import PN532Controller

controller = PN532Controller('/dev/rfcomm0')
controller.connect()

# Read card
card = controller.read_passive_target()
print(f"UID: {card['uid']}")

# Send SELECT PPSE
apdu = bytes.fromhex('00A404000E325041592E5359532E444446303100')
response = controller.transceive_apdu(card['target_number'], apdu)
print(f"Response: {response.hex()}")

controller.disconnect()
```

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| `Permission denied` | `sudo chmod 666 /dev/rfcomm0` |
| `Device busy` | `sudo rfcomm release 0` then rebind |
| `No module 'serial'` | `pip3 install pyserial` |
| `Card not detected` | Check card is ISO14443A, centered on antenna |
| `No firmware response` | Check Bluetooth connection, rebind rfcomm0 |

---

## 📊 Your Testing Flow

1. **Start VSCode Terminal**
2. **Bind PN532:** `sudo rfcomm bind 0 <MAC>`
3. **Choose Scenario:**
   - Card reading: `--mode pn532-reads-card`
   - Emulation: `--mode pn532-emulates`
4. **Place Hardware:** Card on PN532 OR Phone on PN532
5. **Watch Terminal:** See real-time APDU exchanges

---

## 🎉 Ready!

```bash
# Quick test
python3 scripts/pn532_controller.py --mode pn532-reads-card
```

**All 3 scenarios supported for your testing! 🚀**

See `PN532_TESTING_GUIDE.md` for detailed instructions.
