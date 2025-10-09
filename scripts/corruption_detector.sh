#!/bin/bash

# Corruption Detection Script
# Scans project files for common corruption patterns
# Usage: ./corruption_detector.sh [directory]

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCAN_DIR="${1:-$(pwd)}"
CORRUPTION_FOUND=false

log() {
    echo -e "${BLUE}[CORRUPTION_DETECTOR]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
    CORRUPTION_FOUND=true
}

error() {
    echo -e "${RED}[CORRUPTION]${NC} $1"
    CORRUPTION_FOUND=true
}

success() {
    echo -e "${GREEN}[CLEAN]${NC} $1"
}

log "Scanning directory: $SCAN_DIR"
log "Looking for corruption patterns in source files..."

# Find all Kotlin and Java files
find "$SCAN_DIR" -name "*.kt" -o -name "*.java" | while read -r file; do
    if [[ -f "$file" ]]; then
        # Check for multiple package statements
        package_count=$(grep -c "^package " "$file" 2>/dev/null || echo "0")
        if [[ $package_count -gt 1 ]]; then
            error "Multiple package statements in: $file ($package_count found)"
        fi
        
        # Check for duplicate imports
        duplicate_imports=$(sort "$file" | grep "^import " | uniq -d)
        if [[ -n "$duplicate_imports" ]]; then
            error "Duplicate imports in: $file"
            echo "$duplicate_imports" | head -3
        fi
        
        # Check for corruption markers
        if grep -q "Unresolved reference\|Cannot infer a type\|Val cannot be reassigned" "$file" 2>/dev/null; then
            error "Compilation error text found in: $file"
        fi
        
        # Check for malformed syntax patterns
        if grep -q "import.*import\|package.*package" "$file" 2>/dev/null; then
            error "Malformed syntax patterns in: $file"
        fi
        
        # Check for excessive line count (possible duplication)
        line_count=$(wc -l < "$file")
        if [[ $line_count -gt 500 ]]; then
            warn "Unusually large file: $file ($line_count lines)"
        fi
        
        # Check for file size anomalies
        char_count=$(wc -c < "$file")
        if [[ $char_count -gt 50000 ]]; then
            warn "Unusually large file size: $file ($char_count bytes)"
        fi
    fi
done

if [[ $CORRUPTION_FOUND == true ]]; then
    error "Corruption patterns detected in project"
    exit 1
else
    success "No corruption patterns found"
    exit 0
fi
