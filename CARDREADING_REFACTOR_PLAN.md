# CardReadingViewModel Refactor Plan
**Date:** October 12, 2025  
**Scope:** Clean up `executeProxmark3EmvWorkflow()` function  
**Status:** 🚧 IN PROGRESS

## 📋 Current State Analysis

### File Statistics
- **File:** `android-app/src/main/java/com/nfsp00f33r/app/screens/cardreading/CardReadingViewModel.kt`
- **Total Lines:** 4267
- **Target Function:** `executeProxmark3EmvWorkflow()` (lines 285-1950)
- **Function Size:** ~1665 lines (TOO LARGE)
- **Complexity:** HIGH - monolithic, mixed concerns, hard to maintain

### Problems Identified
1. ❌ **Monolithic Function** - 1665 lines in single function violates SRP
2. ❌ **Mixed Concerns** - Workflow logic + parsing + UI updates + database all in one place
3. ❌ **Duplicated Code** - APDU sending, parsing, status updates repeated everywhere
4. ❌ **Hard to Test** - Cannot test individual phases independently
5. ❌ **Hard to Maintain** - Changes require modifying massive function
6. ❌ **Nested Conditionals** - Deep nesting makes logic hard to follow
7. ❌ **Inline Parsing** - Tag extraction mixed with workflow logic

## 🎯 Refactoring Goals

### Primary Objectives
- ✅ Break monolithic function into modular phase functions
- ✅ Single Responsibility Principle for each phase
- ✅ Testable, maintainable, clean architecture
- ✅ Preserve ALL existing functionality
- ✅ No behavioral changes - pure refactoring
- ✅ Follow 7-Phase Universal Laws from memory

### Success Criteria
- Main workflow function ≤ 50 lines
- Each phase function ≤ 200 lines
- All phases independently testable
- BUILD SUCCESSFUL with zero regression
- All EMV data extraction preserved

## 🏗️ Architecture Design

### New Structure

```
executeProxmark3EmvWorkflow() [MAIN ORCHESTRATOR ~50 lines]
├── initializeSession(tag)
├── connectToCard(tag) → IsoDep
├── executePhase1_PpseSelection(isoDep)
├── executePhase2_AidSelection(isoDep)
├── executePhase3_Gpo(isoDep)
├── executePhase4_ReadRecords(isoDep)
├── executePhase4B_ExtendedScan(isoDep)
├── executePhase5_GenerateAc(isoDep)
├── executePhase6_GetData(isoDep)
├── executePhase7_TransactionLogs(isoDep)
└── finalizeSession()
```

### Phase Responsibilities

#### Phase 1: PPSE Selection
- **Input:** IsoDep connection
- **Output:** List<AidEntry>
- **Responsibility:** Select PPSE (2PAY/1PAY fallback), extract AIDs
- **Lines:** ~150

#### Phase 2: Multi-AID Selection  
- **Input:** IsoDep, List<AidEntry>
- **Output:** String (selected AID)
- **Responsibility:** Try all AIDs, select first successful
- **Lines:** ~120

#### Phase 3: GPO (Get Processing Options)
- **Input:** IsoDep, selected AID
- **Output:** GpoResult (AIP, AFL, PAN, etc.)
- **Responsibility:** Build PDOL, execute GPO, parse response
- **Lines:** ~180

#### Phase 4: Read Records
- **Input:** IsoDep, AFL data
- **Output:** List<RecordData>
- **Responsibility:** Parse AFL, read all records intelligently
- **Lines:** ~150

#### Phase 4B: Extended Scan
- **Input:** IsoDep, missing tags
- **Output:** Additional records
- **Responsibility:** Search SFI 1-3 for critical missing tags
- **Lines:** ~130

#### Phase 5: Generate AC
- **Input:** IsoDep, CDOL data
- **Output:** CryptogramData
- **Responsibility:** Build CDOL, generate cryptogram
- **Lines:** ~150

#### Phase 6: GET DATA Primitives
- **Input:** IsoDep
- **Output:** Map<String, String> (additional tags)
- **Responsibility:** Query 12 specific EMV tags
- **Lines:** ~100

#### Phase 7: Transaction Logs
- **Input:** IsoDep, log format
- **Output:** List<TransactionLog>
- **Responsibility:** Read transaction history if supported
- **Lines:** ~120

#### Finalize Session
- **Input:** All collected data
- **Output:** EmvCardData
- **Responsibility:** Create card data, save to database, update UI
- **Lines:** ~100

### Helper Functions (Keep Existing)
- `buildPdolData()`
- `buildCdolData()`
- `buildGenerateAcApdu()`
- `addApduLogEntry()`
- `interpretStatusWord()`
- `displayParsedData()`
- `analyzeAip()`
- `extractXXXFromAllResponses()` methods

## 📝 Implementation Plan

### Phase 1: Preparation (COMPLETE)
- [x] Analyze current function
- [x] Create refactor plan document
- [x] Remember task in MCP memory
- [x] Map all existing functionality

### Phase 2: Extract Phase Functions (IN PROGRESS)
- [ ] Extract Phase 1: PPSE Selection
- [ ] Extract Phase 2: AID Selection  
- [ ] Extract Phase 3: GPO
- [ ] Extract Phase 4: Read Records
- [ ] Extract Phase 4B: Extended Scan
- [ ] Extract Phase 5: Generate AC
- [ ] Extract Phase 6: GET DATA
- [ ] Extract Phase 7: Transaction Logs
- [ ] Extract finalize logic

### Phase 3: Create Main Orchestrator
- [ ] Create new clean `executeProxmark3EmvWorkflow()`
- [ ] Wire all phase functions
- [ ] Add error handling
- [ ] Add progress tracking

### Phase 4: Validation
- [ ] Verify all functionality preserved
- [ ] Check no behavioral changes
- [ ] Validate error handling paths
- [ ] Review session state management

### Phase 5: Testing & Build
- [ ] Build project
- [ ] Fix any compilation errors
- [ ] Test with real card
- [ ] Verify all phases execute correctly

### Phase 6: Cleanup
- [ ] Remove old commented code
- [ ] Add KDoc documentation
- [ ] Format code
- [ ] Final review

### Phase 7: Documentation
- [ ] Update CHANGELOG.md
- [ ] Create summary document
- [ ] Remember completion in MCP

## 🔧 Technical Details

### Data Classes (New)

```kotlin
private data class PpseResult(
    val success: Boolean,
    val mode: String, // "2PAY", "1PAY", "FAILED"
    val extractedAids: List<AidEntry>,
    val statusWord: String
)

private data class AidSelectionResult(
    val selectedAid: String,
    val successfulAids: Int,
    val failedAids: Int
)

private data class GpoResult(
    val aip: String,
    val afl: String,
    val pan: String,
    val cryptogram: String,
    val statusWord: String
)

private data class RecordReadResult(
    val records: List<Map<String, EnrichedTagData>>,
    val recordsRead: Int,
    val panFound: Boolean
)

private data class ExtendedScanResult(
    val additionalRecords: Int,
    val criticalTagsFound: Int,
    val missingTags: List<Pair<String, String>>
)

private data class CryptogramResult(
    val arqc: String,
    val cid: String,
    val atc: String,
    val statusWord: String
)

private data class GetDataResult(
    val tags: Map<String, EnrichedTagData>,
    val successCount: Int,
    val logFormatFound: Boolean,
    val logFormatValue: String
)

private data class TransactionLogResult(
    val logs: List<Map<String, EnrichedTagData>>,
    val logsRead: Int
)
```

### Session State Management
- Keep existing `SessionScanData` class
- Each phase updates `currentSessionData`
- All phases access shared session state
- Error handling preserves session state

### Progress & UI Updates
- Each phase updates `currentPhase`, `progress`, `statusMessage`
- Use `withContext(Dispatchers.Main)` for UI updates
- Consistent progress increments (0.0 → 1.0)

## ⚠️ Critical Rules

### During Refactoring
1. **NO BEHAVIORAL CHANGES** - Pure refactoring only
2. **PRESERVE ALL FUNCTIONALITY** - Every feature must work exactly as before
3. **NO LOGIC MODIFICATIONS** - Only structural changes
4. **BUILD SUCCESSFUL REQUIRED** - Must compile at each step
5. **INCREMENTAL CHANGES** - One phase at a time
6. **TEST AFTER EACH PHASE** - Verify no regression

### Code Quality
1. **PascalCase** for class names
2. **camelCase** for functions/properties
3. **NO safe-call operators** (`?.`) in production code
4. **Explicit null checks** always
5. **KDoc comments** for public functions
6. **Timber logging** for debugging

## 📊 Metrics

### Before Refactor
- Main function: 1665 lines
- Testability: 0/10 (monolithic)
- Maintainability: 2/10 (hard to modify)
- Readability: 3/10 (complex nested logic)
- Code duplication: HIGH

### After Refactor (Target)
- Main function: ≤ 50 lines
- Testability: 9/10 (each phase testable)
- Maintainability: 9/10 (easy to modify)
- Readability: 9/10 (clear flow)
- Code duplication: LOW

## 🎯 Next Steps

1. Start with Phase 1 (PPSE Selection) extraction
2. Test compilation after each extraction
3. Verify functionality preserved
4. Continue phase by phase
5. Create clean main orchestrator
6. Final build & test
7. Document completion

---

**Estimated Time:** 2-3 hours  
**Complexity:** MEDIUM-HIGH  
**Risk:** LOW (pure refactoring, no logic changes)  
**Benefit:** HIGH (much cleaner, maintainable code)
