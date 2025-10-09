#!/bin/bash
# Setup script for PN532 testing environment

echo "🔧 PN532 Testing Environment Setup"
echo "=================================="
echo ""

# Check Python 3
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found. Please install Python 3.8+"
    exit 1
fi

echo "✅ Python 3 found: $(python3 --version)"

# Install Python dependencies
echo ""
echo "📦 Installing Python dependencies..."
pip3 install -r requirements.txt

if [ $? -eq 0 ]; then
    echo "✅ Python dependencies installed"
else
    echo "❌ Failed to install dependencies"
    exit 1
fi

# Check for Bluetooth tools
echo ""
echo "🔍 Checking Bluetooth tools..."

if command -v hcitool &> /dev/null; then
    echo "✅ hcitool found"
else
    echo "⚠️ hcitool not found. Install: sudo apt-get install bluez"
fi

if command -v rfcomm &> /dev/null; then
    echo "✅ rfcomm found"
else
    echo "⚠️ rfcomm not found. Install: sudo apt-get install bluez"
fi

# Check for rfcomm0
echo ""
echo "🔍 Checking for rfcomm0..."
if [ -e /dev/rfcomm0 ]; then
    echo "✅ /dev/rfcomm0 exists"
    ls -l /dev/rfcomm0
    
    # Check permissions
    if [ -r /dev/rfcomm0 ] && [ -w /dev/rfcomm0 ]; then
        echo "✅ /dev/rfcomm0 has read/write permissions"
    else
        echo "⚠️ /dev/rfcomm0 needs permissions. Run: sudo chmod 666 /dev/rfcomm0"
    fi
else
    echo "⚠️ /dev/rfcomm0 not found"
    echo ""
    echo "To bind PN532:"
    echo "  1. Find MAC: hcitool scan"
    echo "  2. Bind: sudo rfcomm bind 0 <MAC_ADDRESS>"
    echo "  3. Fix permissions: sudo chmod 666 /dev/rfcomm0"
fi

# Test script
echo ""
echo "🧪 Testing Python script..."
if python3 scripts/pn532_controller.py --help > /dev/null 2>&1; then
    echo "✅ pn532_controller.py script OK"
else
    echo "❌ pn532_controller.py script has errors"
    exit 1
fi

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Bind PN532 to rfcomm0: sudo rfcomm bind 0 <MAC_ADDRESS>"
echo "  2. Test connection: python3 scripts/pn532_controller.py"
echo "  3. Run scenario: python3 scripts/pn532_controller.py --mode pn532-reads-card"
echo ""
echo "📖 See PN532_TESTING_GUIDE.md for full instructions"
