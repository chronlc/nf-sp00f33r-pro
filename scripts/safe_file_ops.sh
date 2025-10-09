#!/bin/bash

# Safe File Operations Script
# Corruption-free alternatives to VS Code file editing tools
# Usage: ./safe_file_ops.sh <operation> <args...>

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[SAFE_FILE_OPS]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Function: Safe file creation
# Usage: safe_create_file <file_path> <content_from_stdin>
safe_create_file() {
    local file_path="$1"
    local backup_path="${file_path}.backup.$(date +%s)"
    
    log "Creating file: $file_path"
    
    # Create directory if it doesn't exist
    mkdir -p "$(dirname "$file_path")"
    
    # Backup existing file if it exists
    if [[ -f "$file_path" ]]; then
        warn "File exists, creating backup: $backup_path"
        cp "$file_path" "$backup_path"
    fi
    
    # Create the file from stdin
    cat > "$file_path"
    
    # Verify file was created
    if [[ -f "$file_path" ]]; then
        local line_count=$(wc -l < "$file_path")
        success "File created successfully with $line_count lines"
        log "File size: $(wc -c < "$file_path") bytes"
    else
        error "Failed to create file: $file_path"
    fi
}

# Function: Safe string replacement
# Usage: safe_replace_string <file_path> <old_string> <new_string>
safe_replace_string() {
    local file_path="$1"
    local old_string="$2"
    local new_string="$3"
    local backup_path="${file_path}.backup.$(date +%s)"
    
    log "Replacing string in file: $file_path"
    
    # Check if file exists
    if [[ ! -f "$file_path" ]]; then
        error "File does not exist: $file_path"
    fi
    
    # Create backup
    cp "$file_path" "$backup_path"
    log "Backup created: $backup_path"
    
    # Check if old string exists
    if ! grep -qF "$old_string" "$file_path"; then
        warn "Old string not found in file"
        rm "$backup_path"  # Remove unnecessary backup
        return 1
    fi
    
    # Create temporary file for replacement
    local temp_file=$(mktemp)
    
    # Use sed for replacement (with proper escaping)
    # First, escape special characters in the strings
    local escaped_old=$(printf '%s\n' "$old_string" | sed 's/[[\.*^$(){}?+|\\]/\\&/g')
    local escaped_new=$(printf '%s\n' "$new_string" | sed 's/[[\.*^$(){}?+|\\]/\\&/g')
    
    # Perform replacement
    sed "s|$escaped_old|$escaped_new|g" "$file_path" > "$temp_file"
    
    # Verify the replacement worked
    if grep -qF "$new_string" "$temp_file"; then
        mv "$temp_file" "$file_path"
        success "String replacement completed"
        rm "$backup_path"  # Remove backup on success
    else
        rm "$temp_file"
        cp "$backup_path" "$file_path"  # Restore from backup
        rm "$backup_path"
        error "String replacement failed, file restored from backup"
    fi
}

# Function: Safe line-based replacement (more reliable for code)
# Usage: safe_replace_lines <file_path> <start_line> <end_line> <new_content_from_stdin>
safe_replace_lines() {
    local file_path="$1"
    local start_line="$2"
    local end_line="$3"
    local backup_path="${file_path}.backup.$(date +%s)"
    
    log "Replacing lines $start_line-$end_line in file: $file_path"
    
    # Check if file exists
    if [[ ! -f "$file_path" ]]; then
        error "File does not exist: $file_path"
    fi
    
    # Create backup
    cp "$file_path" "$backup_path"
    log "Backup created: $backup_path"
    
    # Create temporary files
    local temp_file=$(mktemp)
    local new_content=$(mktemp)
    
    # Read new content from stdin
    cat > "$new_content"
    
    # Build new file: head + new_content + tail
    head -n $((start_line - 1)) "$file_path" > "$temp_file"
    cat "$new_content" >> "$temp_file"
    tail -n +$((end_line + 1)) "$file_path" >> "$temp_file"
    
    # Replace original file
    mv "$temp_file" "$file_path"
    rm "$new_content"
    
    success "Line replacement completed"
    rm "$backup_path"  # Remove backup on success
}

# Function: Safe file verification
# Usage: safe_verify_file <file_path>
safe_verify_file() {
    local file_path="$1"
    
    log "Verifying file: $file_path"
    
    if [[ ! -f "$file_path" ]]; then
        error "File does not exist: $file_path"
    fi
    
    # Check for common corruption indicators
    local corruption_found=false
    
    # Check for repeated package statements
    local package_count=$(grep -c "^package " "$file_path" 2>/dev/null || echo "0")
    if [[ $package_count -gt 1 ]]; then
        warn "Multiple package statements found ($package_count)"
        corruption_found=true
    fi
    
    # Check for repeated imports
    local duplicate_imports=$(sort "$file_path" | grep "^import " | uniq -d | wc -l)
    if [[ $duplicate_imports -gt 0 ]]; then
        warn "Duplicate import statements found ($duplicate_imports)"
        corruption_found=true
    fi
    
    # Check for common corruption patterns
    if grep -q "Unresolved reference" "$file_path" 2>/dev/null; then
        warn "File contains 'Unresolved reference' text (possible corruption)"
        corruption_found=true
    fi
    
    # Check file integrity
    local line_count=$(wc -l < "$file_path")
    local char_count=$(wc -c < "$file_path")
    
    log "File stats: $line_count lines, $char_count bytes"
    
    if [[ $corruption_found == true ]]; then
        error "File verification failed - corruption detected"
    else
        success "File verification passed"
    fi
}

# Function: Compare with build test
# Usage: safe_build_test <workspace_path>
safe_build_test() {
    local workspace_path="$1"
    
    log "Testing build after file operations..."
    
    cd "$workspace_path/android-app"
    
    # Run clean build
    if ./gradlew clean assembleDebug 2>&1 | grep -q "BUILD SUCCESSFUL"; then
        success "Build test passed"
        return 0
    else
        error "Build test failed"
        return 1
    fi
}

# Main script logic
case "$1" in
    "create")
        if [[ $# -ne 2 ]]; then
            error "Usage: $0 create <file_path>"
        fi
        safe_create_file "$2"
        ;;
    "replace")
        if [[ $# -ne 4 ]]; then
            error "Usage: $0 replace <file_path> <old_string> <new_string>"
        fi
        safe_replace_string "$2" "$3" "$4"
        ;;
    "replace-lines")
        if [[ $# -ne 4 ]]; then
            error "Usage: $0 replace-lines <file_path> <start_line> <end_line>"
        fi
        safe_replace_lines "$2" "$3" "$4"
        ;;
    "verify")
        if [[ $# -ne 2 ]]; then
            error "Usage: $0 verify <file_path>"
        fi
        safe_verify_file "$2"
        ;;
    "build-test")
        if [[ $# -ne 2 ]]; then
            error "Usage: $0 build-test <workspace_path>"
        fi
        safe_build_test "$2"
        ;;
    *)
        echo "Safe File Operations - Corruption-free alternatives"
        echo ""
        echo "Usage: $0 <operation> <args...>"
        echo ""
        echo "Operations:"
        echo "  create <file_path>                    - Create file from stdin"
        echo "  replace <file_path> <old> <new>      - Replace string in file"
        echo "  replace-lines <file> <start> <end>   - Replace lines with stdin content"
        echo "  verify <file_path>                   - Verify file integrity"
        echo "  build-test <workspace_path>          - Test build after changes"
        echo ""
        echo "Examples:"
        echo "  echo 'content' | $0 create /path/to/file.kt"
        echo "  $0 replace /path/to/file.kt 'old text' 'new text'"
        echo "  echo 'new content' | $0 replace-lines /path/to/file.kt 10 15"
        echo "  $0 verify /path/to/file.kt"
        echo "  $0 build-test /path/to/workspace"
        ;;
esac
