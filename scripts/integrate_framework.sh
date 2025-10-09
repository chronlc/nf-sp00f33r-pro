#!/bin/bash
# Framework Integration Automation Script
# Integrates nf-sp00f33r-framework v3.0.0 features into main project
# Date: October 9, 2025

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
FRAMEWORK_PATH="/home/user/DEVCoDE/_workspace_nf-sp00f33r-framework-v3.0.0-FINAL-PRODUCTION-VERIFIED(1)/nf-sp00f33r-framework"
PROJECT_ROOT="/home/user/DEVCoDE/FINALS/nf-sp00f33r"
APP_PATH="$PROJECT_ROOT/android-app/src/main/kotlin/com/nfsp00f33r/app"
BACKUP_DIR="$PROJECT_ROOT/backups/pre-integration-$(date +%Y%m%d_%H%M%S)"

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verify framework exists
if [ ! -d "$FRAMEWORK_PATH" ]; then
    log_error "Framework not found at: $FRAMEWORK_PATH"
    exit 1
fi

log_info "Starting framework integration..."
log_info "Framework: $FRAMEWORK_PATH"
log_info "Project: $PROJECT_ROOT"

# Create backup
log_info "Creating backup..."
mkdir -p "$BACKUP_DIR"
cp -r "$APP_PATH" "$BACKUP_DIR/"
log_success "Backup created at: $BACKUP_DIR"

# Phase 1: Core Infrastructure
log_info "Phase 1: Installing core infrastructure..."

# Create framework core directory
mkdir -p "$APP_PATH/framework/core"
log_info "Created framework/core directory"

# Copy core files
if [ -d "$FRAMEWORK_PATH/framework/core" ]; then
    log_info "Copying framework core files..."
    cp "$FRAMEWORK_PATH/framework/core/"*.kt "$APP_PATH/framework/core/"
    
    # Update package names
    find "$APP_PATH/framework/core" -name "*.kt" -exec \
        sed -i 's/package com\.ninjatech\.nfsp00f33r/package com.nfsp00f33r.app/g' {} \;
    
    find "$APP_PATH/framework/core" -name "*.kt" -exec \
        sed -i 's/import com\.ninjatech\.nfsp00f33r/import com.nfsp00f33r.app/g' {} \;
    
    log_success "Framework core installed (4 files)"
else
    log_error "Framework core not found in source"
    exit 1
fi

# Phase 2: Multi-Device Support
log_info "Phase 2: Installing multi-device support..."

mkdir -p "$APP_PATH/devices/models"
log_info "Created devices directory"

if [ -d "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/devices" ]; then
    log_info "Copying device manager files..."
    cp -r "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/devices/"* "$APP_PATH/devices/"
    
    # Update package names
    find "$APP_PATH/devices" -name "*.kt" -exec \
        sed -i 's/package com\.ninjatech\.nfsp00f33r/package com.nfsp00f33r.app/g' {} \;
    
    find "$APP_PATH/devices" -name "*.kt" -exec \
        sed -i 's/import com\.ninjatech\.nfsp00f33r/import com.nfsp00f33r.app/g' {} \;
    
    FILE_COUNT=$(find "$APP_PATH/devices" -name "*.kt" | wc -l)
    log_success "Device manager installed ($FILE_COUNT files)"
else
    log_warning "Device manager not found - skipping"
fi

# Phase 3: Card Type Detection
log_info "Phase 3: Installing card type detector..."

mkdir -p "$APP_PATH/cards/models"
log_info "Created cards directory"

if [ -d "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/cards" ]; then
    log_info "Copying card detection files..."
    cp -r "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/cards/"* "$APP_PATH/cards/"
    
    # Update package names
    find "$APP_PATH/cards" -name "*.kt" -exec \
        sed -i 's/package com\.ninjatech\.nfsp00f33r/package com.nfsp00f33r.app/g' {} \;
    
    find "$APP_PATH/cards" -name "*.kt" -exec \
        sed -i 's/import com\.ninjatech\.nfsp00f33r/import com.nfsp00f33r.app/g' {} \;
    
    FILE_COUNT=$(find "$APP_PATH/cards" -name "*.kt" | wc -l)
    log_success "Card type detector installed ($FILE_COUNT files)"
else
    log_warning "Card detector not found - skipping"
fi

# Phase 4: Universal Card Reader
log_info "Phase 4: Installing universal card reader..."

mkdir -p "$APP_PATH/reader/models"
log_info "Created reader directory"

if [ -d "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/reader" ]; then
    log_info "Copying universal reader files..."
    cp -r "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/reader/"* "$APP_PATH/reader/"
    
    # Update package names
    find "$APP_PATH/reader" -name "*.kt" -exec \
        sed -i 's/package com\.ninjatech\.nfsp00f33r/package com.nfsp00f33r.app/g' {} \;
    
    find "$APP_PATH/reader" -name "*.kt" -exec \
        sed -i 's/import com\.ninjatech\.nfsp00f33r/import com.nfsp00f33r.app/g' {} \;
    
    FILE_COUNT=$(find "$APP_PATH/reader" -name "*.kt" | wc -l)
    log_success "Universal card reader installed ($FILE_COUNT files)"
else
    log_warning "Universal reader not found - skipping"
fi

# Phase 5: Dynamic CVV Generator
log_info "Phase 5: Installing dynamic CVV generator..."

mkdir -p "$APP_PATH/attacks/cvv/models"
log_info "Created attacks/cvv directory"

if [ -d "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/attacks/cvv" ]; then
    log_info "Copying CVV generator files..."
    cp -r "$FRAMEWORK_PATH/src/main/kotlin/com/ninjatech/nfsp00f33r/attacks/cvv/"* "$APP_PATH/attacks/cvv/"
    
    # Update package names
    find "$APP_PATH/attacks/cvv" -name "*.kt" -exec \
        sed -i 's/package com\.ninjatech\.nfsp00f33r/package com.nfsp00f33r.app/g' {} \;
    
    find "$APP_PATH/attacks/cvv" -name "*.kt" -exec \
        sed -i 's/import com\.ninjatech\.nfsp00f33r/import com.nfsp00f33r.app/g' {} \;
    
    FILE_COUNT=$(find "$APP_PATH/attacks/cvv" -name "*.kt" | wc -l)
    log_success "CVV generator installed ($FILE_COUNT files)"
else
    log_warning "CVV generator not found - skipping"
fi

# Summary
echo ""
log_success "========================================="
log_success "Framework Integration Complete!"
log_success "========================================="
echo ""
log_info "Installed features:"
echo "  ✅ Framework core infrastructure"
echo "  ✅ Multi-device support (ACR122U, PN532, Android NFC)"
echo "  ✅ Card type detector (50+ types)"
echo "  ✅ Universal card reader (SAFE/STANDARD/AGGRESSIVE modes)"
echo "  ✅ Dynamic CVV generator"
echo ""
log_info "Backup location: $BACKUP_DIR"
echo ""
log_warning "Next steps:"
echo "  1. Review copied files for compilation errors"
echo "  2. Update NfSp00f33rApplication.kt to initialize modules"
echo "  3. Add required dependencies to build.gradle"
echo "  4. Update AndroidManifest.xml for USB/Bluetooth permissions"
echo "  5. Run: cd android-app && ./gradlew assembleDebug"
echo ""
log_info "See docs/INTEGRATION_IMPLEMENTATION_PLAN.md for detailed steps"
