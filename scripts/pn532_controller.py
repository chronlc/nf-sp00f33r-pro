#!/usr/bin/env python3
"""
PN532 Terminal Controller via rfcomm0
Controls PN532 NFC module through Bluetooth serial connection for testing scenarios

Test Scenarios:
1. Card Reading: Phone on top of NFC card (Android NFC reads physical card)
2. PN532 Reading: Card on top of PN532 (PN532 reads physical card via Bluetooth)
3. Card Emulation: Phone on top of PN532 (PN532 reads phone's emulated card)

Hardware Setup:
- PN532 module connected via Bluetooth (rfcomm0)
- Physical NFC cards for testing
- Android phone with NFC enabled
- VSCode terminal for control

Usage:
    python pn532_controller.py --port /dev/rfcomm0 --mode [read|emulate|monitor]
"""

import serial
import time
import argparse
import json
import sys
from datetime import datetime
from typing import Optional, List, Tuple
import struct

class PN532Controller:
    """Controls PN532 NFC module via rfcomm0 serial connection"""
    
    # PN532 Commands
    CMD_GET_FIRMWARE_VERSION = b'\x02'
    CMD_SAM_CONFIGURATION = b'\x14'
    CMD_IN_LIST_PASSIVE_TARGET = b'\x4A'
    CMD_IN_DATA_EXCHANGE = b'\x40'
    CMD_IN_RELEASE = b'\x52'
    CMD_TG_INIT_AS_TARGET = b'\x8C'
    CMD_TG_GET_DATA = b'\x86'
    CMD_TG_SET_DATA = b'\x8E'
    
    # Frame markers
    PREAMBLE = b'\x00'
    STARTCODE1 = b'\x00'
    STARTCODE2 = b'\xFF'
    POSTAMBLE = b'\x00'
    
    # ACK/NACK
    ACK = b'\x00\x00\xFF\x00\xFF\x00'
    NACK = b'\x00\x00\xFF\xFF\x00\x00'
    
    def __init__(self, port: str = '/dev/rfcomm0', baudrate: int = 115200, timeout: int = 2):
        """Initialize PN532 controller
        
        Args:
            port: Serial port path (default: /dev/rfcomm0)
            baudrate: Baud rate for serial communication (default: 115200)
            timeout: Read timeout in seconds (default: 2)
        """
        self.port = port
        self.baudrate = baudrate
        self.timeout = timeout
        self.serial = None
        self.debug = True
        
        print(f"🔧 PN532 Controller initialized")
        print(f"   Port: {port}")
        print(f"   Baudrate: {baudrate}")
        print(f"   Timeout: {timeout}s")
    
    def connect(self) -> bool:
        """Connect to PN532 via rfcomm0"""
        try:
            print(f"\n📡 Connecting to PN532 on {self.port}...")
            self.serial = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=self.timeout,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE
            )
            
            # Wait for PN532 to be ready
            time.sleep(0.5)
            
            # Wake up PN532
            self.wakeup()
            
            # Get firmware version to verify connection
            firmware = self.get_firmware_version()
            if firmware:
                print(f"✅ Connected to PN532")
                print(f"   Firmware: {firmware}")
                return True
            else:
                print(f"❌ Failed to get firmware version")
                return False
                
        except serial.SerialException as e:
            print(f"❌ Serial connection failed: {e}")
            print(f"\n💡 Tips:")
            print(f"   1. Check if rfcomm0 is bound: ls -l /dev/rfcomm0")
            print(f"   2. Bind PN532: sudo rfcomm bind 0 <BT_MAC_ADDRESS>")
            print(f"   3. Check permissions: sudo chmod 666 /dev/rfcomm0")
            return False
        except Exception as e:
            print(f"❌ Connection error: {e}")
            return False
    
    def disconnect(self):
        """Disconnect from PN532"""
        if self.serial and self.serial.is_open:
            self.serial.close()
            print("📴 Disconnected from PN532")
    
    def wakeup(self):
        """Wake up PN532 from low power mode"""
        if self.debug:
            print("⏰ Waking up PN532...")
        
        # Send wakeup sequence
        self.serial.write(b'\x55' * 10)
        time.sleep(0.1)
        
        # Flush any responses
        self.serial.reset_input_buffer()
    
    def send_command(self, command: bytes, params: bytes = b'') -> Optional[bytes]:
        """Send command to PN532 and get response
        
        Args:
            command: Command byte
            params: Command parameters
            
        Returns:
            Response data or None if failed
        """
        # Build frame
        frame = self._build_frame(command + params)
        
        if self.debug:
            print(f"📤 TX: {frame.hex().upper()}")
        
        # Send frame
        self.serial.write(frame)
        
        # Wait for ACK
        ack = self.serial.read(6)
        if ack != self.ACK:
            print(f"⚠️ Expected ACK, got: {ack.hex().upper()}")
            return None
        
        if self.debug:
            print(f"✓ ACK received")
        
        # Read response
        response = self._read_frame()
        
        if response and self.debug:
            print(f"📥 RX: {response.hex().upper()}")
        
        return response
    
    def _build_frame(self, data: bytes) -> bytes:
        """Build PN532 frame with checksum"""
        length = len(data) + 1  # +1 for TFI
        
        # Calculate LCS (Length Checksum)
        lcs = (~length + 1) & 0xFF
        
        # TFI (Frame Identifier) = 0xD4 for host to PN532
        tfi = b'\xD4'
        
        # Calculate DCS (Data Checksum)
        checksum_data = tfi + data
        dcs = (~sum(checksum_data) + 1) & 0xFF
        
        # Build frame
        frame = (
            self.PREAMBLE +
            self.STARTCODE1 +
            self.STARTCODE2 +
            bytes([length]) +
            bytes([lcs]) +
            tfi +
            data +
            bytes([dcs]) +
            self.POSTAMBLE
        )
        
        return frame
    
    def _read_frame(self) -> Optional[bytes]:
        """Read response frame from PN532"""
        # Read preamble and start codes
        header = self.serial.read(3)
        if len(header) < 3:
            return None
        
        if header != self.PREAMBLE + self.STARTCODE1 + self.STARTCODE2:
            print(f"⚠️ Invalid header: {header.hex().upper()}")
            return None
        
        # Read length and LCS
        len_lcs = self.serial.read(2)
        if len(len_lcs) < 2:
            return None
        
        length = len_lcs[0]
        lcs = len_lcs[1]
        
        # Verify LCS
        if (length + lcs) & 0xFF != 0:
            print(f"⚠️ Invalid LCS")
            return None
        
        # Read TFI + data + DCS
        data_dcs = self.serial.read(length + 1)
        if len(data_dcs) < length + 1:
            return None
        
        tfi = data_dcs[0]
        data = data_dcs[1:-1]
        dcs = data_dcs[-1]
        
        # Verify DCS
        checksum = (tfi + sum(data) + dcs) & 0xFF
        if checksum != 0:
            print(f"⚠️ Invalid DCS")
            return None
        
        # Read postamble
        postamble = self.serial.read(1)
        
        return data
    
    def get_firmware_version(self) -> Optional[str]:
        """Get PN532 firmware version"""
        response = self.send_command(self.CMD_GET_FIRMWARE_VERSION)
        
        if response and len(response) >= 4:
            ic = response[0]
            ver = response[1]
            rev = response[2]
            support = response[3]
            
            version = f"v{ver}.{rev} (IC: 0x{ic:02X}, Support: 0x{support:02X})"
            return version
        
        return None
    
    def sam_configuration(self) -> bool:
        """Configure SAM (Security Access Module)"""
        if self.debug:
            print("🔧 Configuring SAM (Normal mode)...")
        
        # Mode: Normal (0x01), Timeout: 50ms (0x14), IRQ: enabled (0x01)
        params = b'\x01\x14\x01'
        response = self.send_command(self.CMD_SAM_CONFIGURATION, params)
        
        return response is not None
    
    def read_passive_target(self, card_type: int = 0x00, max_targets: int = 1) -> Optional[dict]:
        """Read passive target (card) in field
        
        Args:
            card_type: 0x00 = ISO14443A (Mifare, EMV), 0x01 = Felica
            max_targets: Maximum number of targets to detect
            
        Returns:
            Card information dict or None
        """
        if self.debug:
            print(f"\n🔍 Scanning for ISO14443A cards (max: {max_targets})...")
        
        params = bytes([max_targets, card_type])
        response = self.send_command(self.CMD_IN_LIST_PASSIVE_TARGET, params)
        
        if not response or len(response) < 2:
            if self.debug:
                print("❌ No cards detected")
            return None
        
        num_targets = response[0]
        if num_targets == 0:
            if self.debug:
                print("❌ No cards detected")
            return None
        
        # Parse card data
        target_number = response[1]
        sens_res = response[2:4]  # SENS_RES (ATQA)
        sel_res = response[4]     # SEL_RES (SAK)
        uid_length = response[5]
        uid = response[6:6+uid_length]
        
        card_info = {
            'target_number': target_number,
            'sens_res': sens_res.hex().upper(),
            'sel_res': f'{sel_res:02X}',
            'uid_length': uid_length,
            'uid': uid.hex().upper(),
            'card_type': self._identify_card_type(sel_res)
        }
        
        if self.debug:
            print(f"✅ Card detected!")
            print(f"   UID: {card_info['uid']}")
            print(f"   Type: {card_info['card_type']}")
            print(f"   SENS_RES: {card_info['sens_res']}")
            print(f"   SEL_RES: {card_info['sel_res']}")
        
        return card_info
    
    def _identify_card_type(self, sak: int) -> str:
        """Identify card type from SAK byte"""
        if sak & 0x20:
            return "ISO14443-4 (EMV/Smart Card)"
        elif sak == 0x08:
            return "MIFARE Classic 1K"
        elif sak == 0x18:
            return "MIFARE Classic 4K"
        elif sak == 0x00:
            return "MIFARE Ultralight"
        elif sak == 0x10:
            return "MIFARE Plus"
        else:
            return f"Unknown (SAK: 0x{sak:02X})"
    
    def transceive_apdu(self, target: int, apdu: bytes) -> Optional[bytes]:
        """Send APDU command to card and receive response
        
        Args:
            target: Target number (from read_passive_target)
            apdu: APDU command bytes
            
        Returns:
            Response bytes or None
        """
        if self.debug:
            print(f"\n💬 Transceiving APDU:")
            print(f"   Command: {apdu.hex().upper()}")
        
        params = bytes([target]) + apdu
        response = self.send_command(self.CMD_IN_DATA_EXCHANGE, params)
        
        if not response or len(response) < 2:
            if self.debug:
                print("❌ No response")
            return None
        
        status = response[0]
        data = response[1:]
        
        if status != 0x00:
            if self.debug:
                print(f"⚠️ Error status: 0x{status:02X}")
            return None
        
        if self.debug:
            print(f"   Response: {data.hex().upper()}")
            if len(data) >= 2:
                sw1 = data[-2]
                sw2 = data[-1]
                print(f"   Status Word: {sw1:02X}{sw2:02X}")
        
        return data
    
    def release_target(self, target: int):
        """Release (deselect) target"""
        if self.debug:
            print(f"📤 Releasing target {target}...")
        
        params = bytes([target])
        self.send_command(self.CMD_IN_RELEASE, params)
    
    def init_as_target(self) -> bool:
        """Initialize PN532 as card emulation target
        
        This allows phone to read PN532 as if it's a card
        """
        if self.debug:
            print("\n🎭 Initializing card emulation mode...")
        
        # Mode flags
        mode = 0x04  # DEP only
        
        # SENS_RES (ATQA) - Mimic EMV card
        sens_res = b'\x00\x04'
        
        # NFCID1t (UID) - Random 4-byte UID
        uid = b'\x08\x12\x34\x56'
        
        # SEL_RES (SAK) - ISO14443-4 compliant
        sel_res = b'\x20'
        
        # Build parameters
        params = bytes([mode]) + sens_res + uid + sel_res
        
        # Optional: Add historical bytes, general bytes (for now, empty)
        params += b'\x00\x00\x00'  # Length of Felica params
        params += b'\x00'  # Length of NFCID3t
        params += b'\x00'  # Length of general bytes
        params += b'\x00'  # Length of historical bytes
        
        response = self.send_command(self.CMD_TG_INIT_AS_TARGET, params)
        
        if response:
            print("✅ Card emulation mode active")
            print("   Phone can now read this PN532 as a card")
            return True
        else:
            print("❌ Failed to enter emulation mode")
            return False
    
    def get_target_data(self) -> Optional[bytes]:
        """Get data sent from phone to emulated card"""
        response = self.send_command(self.CMD_TG_GET_DATA)
        
        if response and len(response) > 1:
            status = response[0]
            data = response[1:]
            
            if status == 0x00:
                if self.debug:
                    print(f"📥 Received from phone: {data.hex().upper()}")
                return data
        
        return None
    
    def set_target_data(self, data: bytes) -> bool:
        """Send data from emulated card to phone"""
        params = data
        response = self.send_command(self.CMD_TG_SET_DATA, params)
        
        if response and response[0] == 0x00:
            if self.debug:
                print(f"📤 Sent to phone: {data.hex().upper()}")
            return True
        
        return False

class TestScenarios:
    """Test scenarios for PN532 + Phone + NFC cards"""
    
    def __init__(self, controller: PN532Controller):
        self.pn532 = controller
    
    def scenario_1_phone_reads_card(self):
        """Scenario 1: Phone on top of NFC card (Android NFC reads physical card)
        
        In this scenario, we just monitor that the phone's NFC is working.
        PN532 is not involved in this scenario.
        """
        print("\n" + "="*60)
        print("📱 SCENARIO 1: Phone Reads Physical NFC Card")
        print("="*60)
        print("\nSetup:")
        print("  1. Place NFC card on table/surface")
        print("  2. Place phone on top of card")
        print("  3. Open nf-sp00f33r app and go to Card Reading screen")
        print("  4. Phone's internal NFC should detect the card")
        print("\n✅ This scenario tests Android NFC (no PN532 involvement)")
        print("   Check app UI for card detection")
    
    def scenario_2_pn532_reads_card(self):
        """Scenario 2: PN532 reads physical card via Bluetooth"""
        print("\n" + "="*60)
        print("🔧 SCENARIO 2: PN532 Reads Physical NFC Card")
        print("="*60)
        print("\nSetup:")
        print("  1. Place NFC card on top of PN532 module")
        print("  2. PN532 will read card via Bluetooth serial")
        print("\nStarting card detection...\n")
        
        # Configure SAM
        if not self.pn532.sam_configuration():
            print("❌ SAM configuration failed")
            return
        
        # Continuous polling
        print("🔄 Polling for cards... (Ctrl+C to stop)\n")
        
        try:
            while True:
                card_info = self.pn532.read_passive_target()
                
                if card_info:
                    print(f"\n🎯 Card Details:")
                    print(f"   UID: {card_info['uid']}")
                    print(f"   Type: {card_info['card_type']}")
                    print(f"   Target: {card_info['target_number']}")
                    
                    # Try to send SELECT PPSE command
                    self.test_emv_commands(card_info['target_number'])
                    
                    # Release target
                    self.pn532.release_target(card_info['target_number'])
                    
                    print("\n✅ Card reading complete")
                    break
                
                time.sleep(1)
                
        except KeyboardInterrupt:
            print("\n\n⏹ Stopped by user")
    
    def scenario_3_pn532_emulates_card(self):
        """Scenario 3: PN532 emulates card, phone reads it"""
        print("\n" + "="*60)
        print("🎭 SCENARIO 3: PN532 Emulates Card (Phone Reads PN532)")
        print("="*60)
        print("\nSetup:")
        print("  1. PN532 enters card emulation mode")
        print("  2. Place phone on top of PN532")
        print("  3. Phone's NFC should detect PN532 as a card")
        print("\nStarting emulation...\n")
        
        # Enter emulation mode
        if not self.pn532.init_as_target():
            print("❌ Failed to enter emulation mode")
            return
        
        print("🔄 Waiting for phone to connect... (Ctrl+C to stop)\n")
        
        try:
            while True:
                # Check if phone sent data
                data = self.pn532.get_target_data()
                
                if data:
                    print(f"\n📱 Phone sent command: {data.hex().upper()}")
                    
                    # Parse APDU if it looks like one
                    if len(data) >= 4:
                        cla = data[0]
                        ins = data[1]
                        p1 = data[2]
                        p2 = data[3]
                        
                        print(f"   CLA: {cla:02X}")
                        print(f"   INS: {ins:02X}")
                        print(f"   P1: {p1:02X}")
                        print(f"   P2: {p2:02X}")
                        
                        # Respond with mock data
                        response = b'\x90\x00'  # Success status word
                        self.pn532.set_target_data(response)
                        print(f"   ↳ Responded: {response.hex().upper()}")
                
                time.sleep(0.1)
                
        except KeyboardInterrupt:
            print("\n\n⏹ Stopped by user")
    
    def test_emv_commands(self, target: int):
        """Send test EMV commands to card"""
        print(f"\n📋 Testing EMV commands...")
        
        # SELECT PPSE
        select_ppse = bytes.fromhex('00A404000E325041592E5359532E444446303100')
        response = self.pn532.transceive_apdu(target, select_ppse)
        
        if response:
            print(f"✅ SELECT PPSE successful")
        else:
            print(f"❌ SELECT PPSE failed")

def main():
    parser = argparse.ArgumentParser(
        description='PN532 Terminal Controller via rfcomm0',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Test Scenarios:
  1. phone-reads-card    : Phone on NFC card (Android NFC active)
  2. pn532-reads-card    : Card on PN532 (Bluetooth serial read)
  3. pn532-emulates      : Phone on PN532 (PN532 emulates card)

Examples:
  # Connect and check firmware
  python pn532_controller.py --port /dev/rfcomm0
  
  # Run card reading test
  python pn532_controller.py --mode pn532-reads-card
  
  # Run emulation test
  python pn532_controller.py --mode pn532-emulates
  
Bluetooth Setup:
  # Find PN532 MAC address
  hcitool scan
  
  # Bind to rfcomm0
  sudo rfcomm bind 0 <MAC_ADDRESS>
  
  # Check binding
  ls -l /dev/rfcomm0
  
  # Fix permissions
  sudo chmod 666 /dev/rfcomm0
        """
    )
    
    parser.add_argument('--port', default='/dev/rfcomm0',
                       help='Serial port (default: /dev/rfcomm0)')
    parser.add_argument('--baudrate', type=int, default=115200,
                       help='Baud rate (default: 115200)')
    parser.add_argument('--mode', choices=['phone-reads-card', 'pn532-reads-card', 'pn532-emulates'],
                       help='Test scenario to run')
    parser.add_argument('--debug', action='store_true', default=True,
                       help='Enable debug output (default: enabled)')
    
    args = parser.parse_args()
    
    print("╔" + "═"*58 + "╗")
    print("║" + " "*15 + "PN532 Terminal Controller" + " "*18 + "║")
    print("║" + " "*17 + "rfcomm0 Serial Control" + " "*18 + "║")
    print("╚" + "═"*58 + "╝")
    
    # Create controller
    controller = PN532Controller(
        port=args.port,
        baudrate=args.baudrate,
        timeout=2
    )
    controller.debug = args.debug
    
    # Connect to PN532
    if not controller.connect():
        print("\n❌ Connection failed. Exiting.")
        sys.exit(1)
    
    # Create test scenarios
    scenarios = TestScenarios(controller)
    
    try:
        if args.mode == 'phone-reads-card':
            scenarios.scenario_1_phone_reads_card()
        elif args.mode == 'pn532-reads-card':
            scenarios.scenario_2_pn532_reads_card()
        elif args.mode == 'pn532-emulates':
            scenarios.scenario_3_pn532_emulates_card()
        else:
            print("\n✅ PN532 connected and ready")
            print("\nAvailable test scenarios:")
            print("  1. phone-reads-card    : Phone on NFC card")
            print("  2. pn532-reads-card    : Card on PN532")
            print("  3. pn532-emulates      : Phone on PN532 (emulation)")
            print("\nRun with --mode <scenario> to start testing")
    
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
    
    finally:
        controller.disconnect()

if __name__ == '__main__':
    main()
