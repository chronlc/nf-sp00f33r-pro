# CardReadingScreen.kt - Complete Dependency Map
**Date:** October 10, 2025  
**File:** `/android-app/src/main/java/com/nfsp00f33r/app/screens/cardreading/CardReadingScreen.kt`  
**Lines:** 972 lines

---

## 📍 EXTERNAL REFERENCES

### Called By:
1. **MainActivity.kt** (line 467)
   - Navigation item 1 (Card Reading tab)
   - Called directly as `CardReadingScreen()`
   - No parameters passed

### Import Statement:
- `import com.nfsp00f33r.app.screens.cardreading.CardReadingScreen`

---

## 🎯 COMPOSABLE HIERARCHY

```
CardReadingScreen (Root) - Line 38
├─ Box (fillMaxSize, background=#0A0A0A)
│  ├─ [Loading State] Text("Loading...")  (if !isInitialized)
│  └─ Column (fillMaxSize, verticalScroll, spacedBy=12.dp)
│     ├─ StatusHeaderCard(viewModel)              - Line 115
│     ├─ ControlPanelCard(viewModel)              - Line 223
│     ├─ RocaVulnerabilityStatusCard(viewModel)   - Line 450 [CONDITIONAL]
│     ├─ ActiveCardsSection(viewModel)            - Line 518 [CONDITIONAL]
│     ├─ EmvDataDisplaySection(viewModel)         - Line 567 [CONDITIONAL]
│     └─ ApduTerminalSection(viewModel)           - Line 680
```

### Conditional Rendering:
- **RocaVulnerabilityStatusCard:** Only if `viewModel.rocaVulnerabilityStatus != null`
- **ActiveCardsSection:** Only if `viewModel.scannedCards.isNotEmpty()`
- **EmvDataDisplaySection:** Only if `viewModel.parsedEmvFields.isNotEmpty()`

---

## 🔗 STATE DEPENDENCIES (From CardReadingViewModel)

### Directly Used State Variables:

| State Variable | Type | Used In Composables | Read/Write |
|---------------|------|-------------------|-----------|
| `scanState` | `ScanState` enum | StatusHeaderCard, ControlPanelCard | READ |
| `scannedCards` | `List<VirtualCard>` | StatusHeaderCard, ActiveCardsSection | READ |
| `apduLog` | `List<ApduLogEntry>` | StatusHeaderCard, ApduTerminalSection | READ |
| `parsedEmvFields` | `Map<String, String>` | EmvDataDisplaySection | READ |
| `statusMessage` | `String` | ControlPanelCard | READ |
| `selectedReader` | `ReaderType?` | StatusHeaderCard, ControlPanelCard | READ |
| `availableReaders` | `List<ReaderType>` | ControlPanelCard | READ |
| `selectedTechnology` | `NfcTechnology` | ControlPanelCard | READ |
| `rocaVulnerabilityStatus` | `String?` | StatusHeaderCard, RocaVulnerabilityStatusCard | READ |
| `isRocaVulnerable` | `Boolean` | StatusHeaderCard, RocaVulnerabilityStatusCard | READ |

### ViewModel Functions Called:

| Function | Parameters | Called From | Purpose |
|---------|-----------|-------------|---------|
| `getReaderDisplayName()` | `ReaderType` | StatusHeaderCard, ControlPanelCard | Get display name for reader |
| `selectReader()` | `ReaderType` | ControlPanelCard | Set selected reader |
| `selectTechnology()` | `NfcTechnology` | ControlPanelCard | Set NFC technology |
| `startScanning()` | None | ControlPanelCard | Begin card scan |
| `stopScanning()` | None | ControlPanelCard | Stop card scan |

---

## 🧩 COMPOSABLE FUNCTION DETAILS

### 1. **CardReadingScreen()** - Line 38
**Parameters:** None  
**Returns:** Unit  
**State:**
- `isInitialized: Boolean` (local) - Delayed rendering optimization
- `viewModel: CardReadingViewModel` (remembered) - Main state container

**Side Effects:**
- `LaunchedEffect(Unit)` - 50ms delay before marking initialized

**Child Composables:** All others

---

### 2. **StatusHeaderCard(viewModel)** - Line 115
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**Reads:**
- `viewModel.scanState`
- `viewModel.scannedCards.size`
- `viewModel.apduLog.size`
- `viewModel.selectedReader`
- `viewModel.rocaVulnerabilityStatus`
- `viewModel.isRocaVulnerable`
- `MainActivity.currentNfcTag` (static)
- `MainActivity.nfcDebugMessage` (static)

**Calls:**
- `DataStat()` composable (5 times)

**Layout:** Card > Column > Row (header) + Row (stats) + Text (debug)

---

### 3. **DataStat(label, value, color)** - Line 209
**Parameters:**
- `label: String` - Stat label
- `value: String` - Stat value
- `color: Color` - Display color

**Returns:** Unit  
**Layout:** Column > Text (value) + Text (label)

---

### 4. **ControlPanelCard(viewModel)** - Line 223
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**State:**
- `readerExpanded: Boolean` (local)
- `techExpanded: Boolean` (local)

**Reads:**
- `viewModel.selectedReader`
- `viewModel.availableReaders`
- `viewModel.selectedTechnology`
- `viewModel.scanState`
- `viewModel.statusMessage`

**Writes:**
- Calls `viewModel.selectReader()`
- Calls `viewModel.selectTechnology()`
- Calls `viewModel.startScanning()`
- Calls `viewModel.stopScanning()`

**Layout:** Card > Column > Row (dropdowns) + Button (scan) + Text (status)

---

### 5. **RocaVulnerabilityStatusCard(viewModel)** - Line 450
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**Reads:**
- `viewModel.isRocaVulnerable`
- `viewModel.rocaVulnerabilityStatus`

**Conditional:** Only rendered if `rocaVulnerabilityStatus != null`  
**Layout:** Card > Row > Icon + Column

---

### 6. **ActiveCardsSection(viewModel)** - Line 518
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**Reads:**
- `viewModel.scannedCards`

**Conditional:** Only rendered if `scannedCards.isNotEmpty()`  
**Calls:** `VirtualCardView(card)` for each card  
**Layout:** Column > Text (header) + LazyRow (cards) + Text (pagination)

---

### 7. **ApduTerminalSection(viewModel)** - Line 680
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**Reads:**
- `viewModel.apduLog`

**Calls:**
- `ApduLogItemParsed(apduEntry)` for each APDU

**Layout:** Card > Column > Row (header) + Box (terminal) > LazyColumn

---

### 8. **ApduLogItemParsed(apduEntry)** - Line 736
**Parameters:** `apduEntry: ApduLogEntry`  
**Returns:** Unit  
**Calls:**
- `EmvTagDictionary.enhanceApduDescription()`
- `decodeStatusWord()` (local function)
- `parseApduResponseTags()` (local function)

**Layout:** Column > Row (TX) + Row (RX) + Row (parsed tags)

---

### 9. **EmvDataDisplaySection(viewModel)** - Line 828
**Parameters:** `viewModel: CardReadingViewModel`  
**Returns:** Unit  
**Reads:**
- `viewModel.parsedEmvFields`

**Calls:**
- `EmvTlvParser.getRocaAnalysisResults()`
- `RocaVulnerabilityCard(analysisResult)` for each ROCA result
- `EmvFieldRow(key, value)` for each field

**Conditional:** Only rendered if `parsedEmvFields.isNotEmpty()`  
**Data Filtering:**
- `cardData`: pan, expiry_date, cardholder_name, track2, service_code
- `appData`: aip, afl, application_usage_control, application_version, df_name
- `cryptoData`: application_cryptogram, cid, atc, iad
- `rocaResults`: From EmvTlvParser
- `remainingFields`: Everything else (excludes raw_* fields)

**Layout:** Card > Column > LazyColumn (grouped fields)

---

### 10. **EmvFieldRow(key, value)** - Line 949
**Parameters:**
- `key: String` - Field key
- `value: String` - Field value

**Returns:** Unit  
**Calls:**
- `EmvTagDictionary.getTagDescription(key)` if key is hex

**Layout:** Row > Text (key) + Text (value)

---

## 🛠️ HELPER FUNCTIONS (Not Composables)

### 1. **decodeStatusWord(sw: String)** - Line 777
**Parameters:** `sw: String` - Status word (e.g., "9000")  
**Returns:** `Pair<Color, String>` - Color and description  
**Logic:** 40+ case matching for ISO 7816-4 status words  
**Used By:** ApduLogItemParsed

---

### 2. **parseApduResponseTags(response: String)** - Line 828
**Parameters:** `response: String` - Hex response  
**Returns:** `List<Pair<String, String>>` - Tag/description pairs  
**Calls:**
- `EmvTlvParser.parseEmvTlvData()`
- `EmvTagDictionary.getTagDescription()`

**Used By:** ApduLogItemParsed

---

## 📦 EXTERNAL DEPENDENCIES

### Components (External Files):
1. **VirtualCardView** (`com.nfsp00f33r.app.components.VirtualCardView`)
   - Parameters: `card: VirtualCard`
   - Used in: ActiveCardsSection

2. **RocaVulnerabilityCard** (`com.nfsp00f33r.app.ui.components.RocaVulnerabilityCard`)
   - Parameters: `analysisResult: RocaVulnerabilityAnalyzer.RocaAnalysisResult`
   - Used in: EmvDataDisplaySection

3. **RocaVulnerabilityBadge** (IMPORTED BUT NOT USED - Can be removed)

### Utilities:
1. **EmvTagDictionary** (`com.nfsp00f33r.app.cardreading.EmvTagDictionary`)
   - `getTagDescription(tag: String): String`
   - `enhanceApduDescription(command, response, originalDescription): String`

2. **EmvTlvParser** (`com.nfsp00f33r.app.cardreading.EmvTlvParser`)
   - `parseEmvTlvData(data: ByteArray, context: String, validateTags: Boolean): TlvParseResult`
   - `getRocaAnalysisResults(): Map<String, RocaVulnerabilityAnalyzer.RocaAnalysisResult>`

### Android/System:
1. **MainActivity** (static access)
   - `MainActivity.currentNfcTag`
   - `MainActivity.nfcDebugMessage`

---

## 🎨 COLOR PALETTE (All Hard-Coded)

| Color | Hex | Usage |
|-------|-----|-------|
| Background | #0A0A0A | Main background |
| Card Background | #0F1419 | Most cards |
| Terminal Background | #000000 | APDU terminal |
| Success Green | #00FF41 | Headers, success states |
| Error Red | #FF1744 | Errors, scanning state |
| Blue | #2196F3, #4FC3F7 | Info, responses |
| Orange | #FFB74D | Warnings, parsed tags |
| Gray | #888888, #666666, #333333 | Secondary text, borders |
| Dark Green | #1B4332, #4A1A1A | ROCA safe/vulnerable backgrounds |

---

## 📐 SPACING SYSTEM (Inconsistent)

| Value | Usage Count | Used For |
|-------|-------------|---------|
| 4.dp | Several | Small gaps in APDU log |
| 6.dp | Several | Element spacing, rounded corners |
| 8.dp | Several | Card corners, small gaps |
| 12.dp | Many | Main column spacing, card padding |
| 16.dp | Many | Card padding, horizontal spacing |
| 36.dp | 2 | Button height |
| 40.dp | 1 | Scan button height |
| 300.dp | 2 | Terminal height, max EMV data height |

---

## ⚠️ POTENTIAL ISSUES FOUND

### 1. **Unused Import**
- `RocaVulnerabilityBadge` imported but never used

### 2. **Magic Numbers**
- 40+ hard-coded colors
- Inconsistent spacing (4dp, 6dp, 8dp, 12dp, 16dp mixed)
- 50ms delay magic number in initialization

### 3. **Large Functions**
- `decodeStatusWord()`: 60+ lines (40 cases)
- `EmvDataDisplaySection()`: 150+ lines
- `ApduTerminalSection()`: 56 lines

### 4. **Multiple Data Filtering**
Same data filtered 5 times in `EmvDataDisplaySection`:
```kotlin
val cardData = viewModel.parsedEmvFields.filter { ... }
val appData = viewModel.parsedEmvFields.filter { ... }
val cryptoData = viewModel.parsedEmvFields.filter { ... }
// ... etc
```

### 5. **Static Access**
Direct static access to MainActivity properties:
- `MainActivity.currentNfcTag`
- `MainActivity.nfcDebugMessage`

### 6. **Initialization Delay**
```kotlin
LaunchedEffect(Unit) {
    delay(50)
    isInitialized = true
}
```
Modern Compose doesn't need this optimization.

---

## ✅ REFACTORING SAFETY CHECKLIST

### Can Safely Refactor:
- ✅ Extract colors to theme object
- ✅ Standardize spacing to 4/8/12/16/24dp system
- ✅ Split large composables into smaller ones
- ✅ Move `decodeStatusWord()` to separate utility
- ✅ Remove unused `RocaVulnerabilityBadge` import
- ✅ Optimize data filtering in `EmvDataDisplaySection`
- ✅ Remove initialization delay (not needed)

### Must Keep Intact:
- ❌ ViewModel state variable names (used by ViewModel)
- ❌ Function signatures called by ViewModel
- ❌ External component interfaces (VirtualCardView, RocaVulnerabilityCard)
- ❌ EmvTagDictionary/EmvTlvParser function calls
- ❌ Composable hierarchy (parent-child relationships)
- ❌ Conditional rendering logic

### Risky Changes:
- ⚠️ Changing composable parameter names
- ⚠️ Changing state observation patterns
- ⚠️ Modifying data filtering keys (must match ViewModel emvTags keys)
- ⚠️ Altering APDU log parsing logic

---

## 🔄 DATA FLOW DIAGRAM

```
MainActivity (Navigation)
    ↓
CardReadingScreen (Root)
    ↓
CardReadingViewModel (State Source)
    ↓ [State Variables]
    ├─→ scanState ────────────→ StatusHeaderCard, ControlPanelCard
    ├─→ scannedCards ─────────→ StatusHeaderCard, ActiveCardsSection
    ├─→ apduLog ──────────────→ StatusHeaderCard, ApduTerminalSection
    ├─→ parsedEmvFields ──────→ EmvDataDisplaySection
    ├─→ statusMessage ────────→ ControlPanelCard
    ├─→ selectedReader ───────→ StatusHeaderCard, ControlPanelCard
    ├─→ availableReaders ─────→ ControlPanelCard
    ├─→ selectedTechnology ───→ ControlPanelCard
    ├─→ rocaVulnerabilityStatus → StatusHeaderCard, RocaVulnerabilityStatusCard
    └─→ isRocaVulnerable ─────→ StatusHeaderCard, RocaVulnerabilityStatusCard
    
    ↓ [Function Calls]
    ├─→ getReaderDisplayName() ← ControlPanelCard
    ├─→ selectReader() ────────← ControlPanelCard
    ├─→ selectTechnology() ────← ControlPanelCard
    ├─→ startScanning() ───────← ControlPanelCard
    └─→ stopScanning() ────────← ControlPanelCard

External Utilities:
    ├─→ EmvTagDictionary.getTagDescription()
    ├─→ EmvTagDictionary.enhanceApduDescription()
    ├─→ EmvTlvParser.parseEmvTlvData()
    └─→ EmvTlvParser.getRocaAnalysisResults()

External Components:
    ├─→ VirtualCardView (card: VirtualCard)
    └─→ RocaVulnerabilityCard (analysisResult: RocaAnalysisResult)
```

---

## 📊 STATISTICS

- **Total Lines:** 972
- **Composable Functions:** 10
- **Helper Functions:** 2
- **State Variables Read:** 10
- **ViewModel Functions Called:** 5
- **External Dependencies:** 6
- **Hard-Coded Colors:** 40+
- **Magic Numbers:** 15+
- **Largest Function:** EmvDataDisplaySection (150+ lines)

---

## 🎯 RECOMMENDED REFACTORING PLAN

### Phase 1: Low Risk (No Breaking Changes)
1. Extract colors to `CardReadingColors` object
2. Standardize spacing to theme system
3. Remove unused `RocaVulnerabilityBadge` import
4. Remove initialization delay

### Phase 2: Medium Risk (Structural Changes)
5. Split `EmvDataDisplaySection` into sub-composables
6. Move `decodeStatusWord` to `ApduStatusUtils.kt`
7. Optimize data filtering (pre-filter once)

### Phase 3: Code Quality (No Functional Changes)
8. Add kdoc comments to composables
9. Extract string constants
10. Add composable preview functions

---

**END OF MAP**
