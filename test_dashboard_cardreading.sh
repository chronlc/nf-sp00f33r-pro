#!/bin/bash

# Debug Test Script for Dashboard + Card Reading Screens
# nf-sp00f33r EMV Security Research Platform
# Date: October 9, 2025

echo "🏴‍☠️ nf-sp00f33r Debug Test Script"
echo "=================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Check if app is running
echo -e "${YELLOW}[TEST 1]${NC} Checking if app is running..."
APP_PID=$(adb shell ps | grep "com.nfsp00f33r.app" | awk '{print $2}')
if [ -z "$APP_PID" ]; then
    echo -e "${RED}❌ App not running${NC}"
    echo "Launching app..."
    adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity
    sleep 3
else
    echo -e "${GREEN}✅ App running (PID: $APP_PID)${NC}"
fi

# Test 2: Check current activity
echo ""
echo -e "${YELLOW}[TEST 2]${NC} Checking current activity..."
CURRENT_ACTIVITY=$(adb shell dumpsys window windows | grep "mCurrentFocus" | awk -F '/' '{print $2}' | awk '{print $1}')
echo -e "Current Activity: ${GREEN}$CURRENT_ACTIVITY${NC}"

# Test 3: Get NFC adapter status
echo ""
echo -e "${YELLOW}[TEST 3]${NC} Checking NFC hardware status..."
NFC_STATUS=$(adb shell dumpsys nfc | grep "mState" | head -1)
echo -e "NFC Status: ${GREEN}$NFC_STATUS${NC}"

# Test 4: Get Bluetooth adapter status
echo ""
echo -e "${YELLOW}[TEST 4]${NC} Checking Bluetooth hardware status..."
BT_STATUS=$(adb shell dumpsys bluetooth_manager | grep "enabled" | head -1)
echo -e "Bluetooth Status: ${GREEN}$BT_STATUS${NC}"

# Test 5: Check for crashes/errors in logcat
echo ""
echo -e "${YELLOW}[TEST 5]${NC} Checking for recent errors..."
echo "Last 10 error/exception logs:"
adb logcat -d | grep -E "(nfsp00f33r.*(ERROR|Exception|FATAL))" | tail -10
if [ $? -eq 0 ]; then
    echo -e "${YELLOW}⚠️  Some errors found (see above)${NC}"
else
    echo -e "${GREEN}✅ No recent errors${NC}"
fi

# Test 6: Navigate to Dashboard tab
echo ""
echo -e "${YELLOW}[TEST 6]${NC} Testing Dashboard navigation..."
adb shell input tap 200 2400  # Dashboard tab
sleep 1
echo -e "${GREEN}✅ Tapped Dashboard tab${NC}"

# Test 7: Navigate to Card Reading tab
echo ""
echo -e "${YELLOW}[TEST 7]${NC} Testing Card Reading navigation..."
adb shell input tap 400 2400  # Card Reading tab
sleep 1
echo -e "${GREEN}✅ Tapped Card Reading tab${NC}"

# Test 8: Navigate back to Dashboard
echo ""
echo -e "${YELLOW}[TEST 8]${NC} Navigating back to Dashboard..."
adb shell input tap 200 2400
sleep 1
echo -e "${GREEN}✅ Returned to Dashboard${NC}"

# Test 9: Capture screenshot
echo ""
echo -e "${YELLOW}[TEST 9]${NC} Capturing screenshot..."
adb shell screencap -p /sdcard/debug_dashboard.png
adb pull /sdcard/debug_dashboard.png /tmp/ 2>/dev/null
if [ -f "/tmp/debug_dashboard.png" ]; then
    echo -e "${GREEN}✅ Screenshot saved to /tmp/debug_dashboard.png${NC}"
else
    echo -e "${RED}❌ Screenshot failed${NC}"
fi

# Test 10: Get memory usage
echo ""
echo -e "${YELLOW}[TEST 10]${NC} Checking memory usage..."
MEM_INFO=$(adb shell dumpsys meminfo com.nfsp00f33r.app | grep "TOTAL" | head -1)
echo -e "Memory Usage: ${GREEN}$MEM_INFO${NC}"

echo ""
echo "=================================="
echo -e "${GREEN}✅ Debug tests complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Review screenshot: /tmp/debug_dashboard.png"
echo "  2. Check logcat: adb logcat | grep nfsp00f33r"
echo "  3. Test NFC card detection manually"
echo ""
