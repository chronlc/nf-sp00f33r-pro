# CODE GENERATION RULES (Universal Laws)

**Created:** October 7, 2025  
**Purpose:** Enforce systematic, precise, validated approach to ALL code generation  
**Authority:** Universal Law - NO EXCEPTIONS

---

## FUNDAMENTAL PRINCIPLE

**PRECISION OVER SPEED. VALIDATION OVER ASSUMPTION. SYSTEMATIC OVER AD-HOC.**

The root cause of compilation errors is NOT lack of knowledge - it's **lack of discipline**:
- **GUESSING** instead of **READING**
- **ASSUMING** instead of **VALIDATING**
- **INFERRING** instead of **VERIFYING**
- **IMPROVISING** instead of **PLANNING**

This applies to ALL code generation: UI, business logic, data models, tests, APIs, migrations, refactoring.

---

## THE 3-PHASE SYSTEMATIC APPROACH

**EVERY code generation task follows this sequence. NO EXCEPTIONS.**

---

### **PHASE 1: MAPPING (Understand the Territory)**

**Purpose:** Build complete mental model BEFORE touching keyboard  
**Rule:** If you can't explain it, you don't understand it enough to code it

#### Step 1.1: Identify ALL Dependencies
- What classes/interfaces/modules does this code interact with?
- What APIs/frameworks/libraries will be used?
- What data structures flow in and out?

#### Step 1.2: Read ALL Definitions
- Open and READ complete source files (not grep, not search - READ)
- For each class: note exact property names, types, nullability
- For each method: note exact signature (params, return type, exceptions)
- For each API: note exact usage patterns and constraints

#### Step 1.3: Document the Map
- Create written summary of what you discovered
- List exact names, types, signatures you'll use
- Note any type conversions or adaptations needed
- **If you're guessing ANY name/type/signature → STOP, go read the definition**

**Example - Mapping BEFORE Generating Screen:**
```
Screen: MifareAttackScreen
Dependencies Identified:
  - MifareAttackViewModel
  - AttackUiState (sealed class)
  - MifareAttackType (enum)
  - CardInfo (data class)

Read MifareAttackViewModel.kt:
  StateFlows:
    - attackType: StateFlow<MifareAttackType>
    - knownKeys: StateFlow<Map<Int, ByteArray>>  ← Map key is Int, not String
    - dumpResult: StateFlow<List<Byte>?>         ← Nullable List<Byte>
  
  Methods:
    - setKnownKey(key: ByteArray, sector: Int)   ← NOT ByteArray?, requires non-null
    - executeAttack(type: MifareAttackType, params: Map<String, Any>)
    - resetState()

Read CardInfo.kt:
  Properties:
    - uid: ByteArray                             ← Not String, not List<Byte>
    - cardType: String                           ← String, not enum
    - technology: String

Now I can generate with EXACT names. No guessing.
```

---

### **PHASE 2: GENERATION (Build with Precision)**

**Purpose:** Write code that compiles first try  
**Rule:** Use ONLY the exact names/types/signatures from Phase 1 mapping

#### Step 2.1: Follow Your Map
- Reference your Phase 1 documentation
- Copy exact names character-by-character
- Use exact types (don't assume String when it's String?)
- Match exact signatures (don't guess parameter order)

#### Step 2.2: Handle Type Conversions Explicitly
- If types don't match → explicit conversion required
- Long → String? Use SimpleDateFormat or .toString()
- Int → String? Use .toString()
- Map<Int,X> → Map<String,X>? Use .mapKeys { it.key.toString() }
- **Never rely on implicit conversions**

#### Step 2.3: Apply Language/Framework Rules Consistently
- Kotlin nullability: Inside null-check no safe-calls, outside always safe-calls
- Compose scope: Each @Composable has own remember{} states
- When expressions: All branches return same type
- **No shortcuts, no "it'll probably work"**

**Example - Generation WITH Precision:**
```kotlin
// PHASE 1 told me: knownKeys is StateFlow<Map<Int, ByteArray>>
val knownKeys by viewModel.knownKeys.collectAsState()
// ✅ Exact type from mapping

// PHASE 1 told me: setKnownKey expects (ByteArray, Int) not (ByteArray?, Int)
viewModel.setKnownKey(keyBytes ?: byteArrayOf(), sector)
// ✅ Explicit null handling, exact signature

// PHASE 1 told me: uid is ByteArray not String
Text("UID: ${uid.joinToString("") { "%02X".format(it) }}")
// ✅ Explicit conversion, exact type
```

---

### **PHASE 3: VALIDATION (Verify Before Commit)**

**Purpose:** Catch errors BEFORE compilation, not after  
**Rule:** Self-review is mandatory, not optional

#### Step 3.1: Code Review Against Map
- Compare generated code line-by-line with Phase 1 documentation
- Every property access → verify name matches definition
- Every method call → verify signature matches definition
- Every type usage → verify matches expected type

#### Step 3.2: Check for Common Mistakes
- [ ] Any property names that "look right" but weren't in definition?
- [ ] Any type conversions missing? (Long used as String?)
- [ ] Any nullable types used as non-nullable?
- [ ] Any assumptions about parameter order?
- [ ] Any scope violations? (parent state used in child composable?)

#### Step 3.3: Compile and Verify
- Run compilation IMMEDIATELY after generation
- If ANY errors → analyze which phase failed:
  - Mapping incomplete? → Back to Phase 1
  - Precision lacking? → Back to Phase 2
  - Validation missed? → Improve Phase 3 checklist
- **Success = BUILD SUCCESSFUL on first try**

**Example - Validation Checklist:**
```
Generated MifareAttackScreen.kt (450 lines)

Self-Review:
✅ Line 45: viewModel.knownKeys - matches Phase 1 map (StateFlow<Map<Int,ByteArray>>)
✅ Line 78: viewModel.setKnownKey(key ?: byteArrayOf(), sector) - matches signature
✅ Line 112: card.uid.joinToString() - uid is ByteArray per Phase 1
❌ Line 156: Text(text = timestamp) - timestamp is Long, needs conversion!

FIX Line 156:
Text(text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp)))

Re-validate:
✅ All property names match Phase 1 definitions
✅ All method signatures match Phase 1 definitions
✅ All type conversions explicit

Compile:
./gradlew compileDebugKotlin
→ BUILD SUCCESSFUL
```

---

## UNIVERSAL PRECISION PRINCIPLES

These apply to ALL code generation tasks: UI, APIs, data models, business logic, tests, migrations.

---

### 🔴 PRINCIPLE 1: EXPLICIT OVER IMPLICIT

**Core Truth:** Compilers are literal. Humans guess. Be the compiler.

**Rules:**
- If API expects String, pass String (not Long/Int hoping for conversion)
- If method expects non-null, provide non-null (not nullable hoping it works)
- If type is T, use T (not "something similar to T")

**Why it matters:** Implicit assumptions create compilation errors. Explicit code compiles first try.

**Examples:**
```kotlin
// IMPLICIT (hoping it works)
Text(text = timestamp)                    // Maybe it converts Long→String?
viewModel.setKey(nullableKey)             // Maybe it accepts nullable?
val items = source                        // Maybe types are compatible?

// EXPLICIT (guaranteed to work)
Text(text = timestamp.toString())         // Explicit conversion
viewModel.setKey(nullableKey ?: default)  // Explicit null handling
val items = source.map { it.toTarget() }  // Explicit transformation
```

---

### 🔴 PRINCIPLE 2: READ BEFORE WRITE

**Core Truth:** You cannot use what you haven't verified exists.

**Rules:**
- NEVER access a property without reading the class definition
- NEVER call a method without reading the signature
- NEVER use an API without reading the documentation/code
- "It should have a property called X" → NO. Read. Verify. Then use.

**Why it matters:** Guessed names are almost always wrong. Read names are always right.

**Anti-Pattern:**
```
1. Assume class has property "userId"
2. Generate code using "userId"
3. Compile fails: property is actually "id"
4. Spend time fixing
```

**Correct Pattern:**
```
1. Read class definition
2. Find property is named "id"
3. Generate code using "id"
4. Compiles first try
```

---

### 🔴 PRINCIPLE 3: MAP BEFORE CODE

**Core Truth:** Writing code without a plan is debugging by accident.

**Rules:**
- Before generating ANY file, write down what you're building
- List ALL dependencies (classes, APIs, libraries)
- Document ALL names/types/signatures you'll use
- If you can't write the map, you're not ready to code

**Why it matters:** The map catches errors before they become compilation errors.

**Workflow:**
```
WITHOUT MAP:                    WITH MAP:
Generate code                   Write map
↓                               ↓
Compile                         Review map
↓                               ↓
20 errors                       Generate from map
↓                               ↓
Fix errors                      Compile
↓                               ↓
Still 10 errors                 BUILD SUCCESSFUL
↓                               ✅ Done
Keep fixing
↓
Finally builds
❌ Wasted time
```

---

### 🔴 PRINCIPLE 4: VALIDATE BEFORE COMMIT

**Core Truth:** Self-review finds bugs. Compilation finds bugs. Choose self-review (it's faster).

**Rules:**
- After generating code, REVIEW it line-by-line
- Check every property name against your map
- Check every method call against your map
- Check every type against your map
- If ANY line makes you think "I hope this works" → FIX IT NOW

**Why it matters:** Finding errors in review takes seconds. Finding in compilation takes minutes.

**Self-Review Checklist:**
```
For EVERY property access:
  ❓ Did I read the class definition?
  ❓ Is this the exact property name from definition?
  ❓ Is this the exact type from definition?

For EVERY method call:
  ❓ Did I read the method signature?
  ❓ Are parameters in correct order?
  ❓ Are parameter types correct?
  ❓ Is return type handled correctly?

For EVERY type usage:
  ❓ Is nullability correct?
  ❓ Are conversions explicit?
  ❓ Does target API accept this type?
```

---

### 🔴 PRINCIPLE 5: FAIL FAST, LEARN FAST

**Core Truth:** Errors are teachers. Ignore the lesson, repeat the class.

**Rules:**
- If compilation fails, STOP and analyze
- Which principle was violated? (Explicit? Read? Map? Validate?)
- What was guessed that should have been verified?
- Update your process to prevent THIS error type forever

**Why it matters:** Same mistakes repeated = process broken. Fix process, not just code.

**Learning Loop:**
```
Error occurs
↓
Analyze: Why did I generate wrong code?
↓
Identify: Which principle did I violate?
↓
Document: What should I have done differently?
↓
Update: Add check to prevent this error type
↓
Apply: Use updated process on next task
↓
Result: Error type eliminated
```

---

### 🔴 PRINCIPLE 6: RIPPLE EFFECT MANAGEMENT (Consumer Impact Analysis)

**Core Truth:** When you change a provider, you MUST update ALL consumers. Broken consumers = incomplete work.

**Rules:**
- BEFORE changing any API/class/architecture → identify ALL consumers
- AFTER changing provider → update ALL consumer code immediately
- NO EXCEPTIONS: Provider change + consumer updates = ATOMIC OPERATION
- If you can't update consumers immediately → DON'T change the provider yet

**Why it matters:** Half-updated code = broken codebase. Either update everything or update nothing.

**Ripple Effect Protocol:**

**Step 1: Identify Consumers (BEFORE Provider Change)**
```bash
# Find all files that use the class/API you're about to change
grep -r "OldClassName" src/
grep -r "oldMethodName" src/

# Document ALL consumers
Consumers identified:
- MainActivity.kt (10 usages)
- DashboardScreen.kt (5 usages)  
- SettingsViewModel.kt (3 usages)
Total: 18 changes required
```

**Step 2: Plan Consumer Updates**
```
For each consumer, document required changes:

MainActivity.kt changes needed:
1. Import: OldClass → NewClass
2. Variable: oldManager → newManager  
3. Method calls: oldMethod() → newMethod()
4. State observation: oldState → newState

Estimated time: 5 minutes per file × 3 files = 15 minutes
```

**Step 3: Execute Atomically**
```
1. Change provider (API/class/architecture)
2. IMMEDIATELY update ALL consumers (no delays)
3. Build and verify BEFORE moving to next task
4. If ANY consumer breaks → FIX IT NOW

NOT ALLOWED:
- "I'll update consumers later"
- "Let's test the provider first"  
- "Users can update consumers themselves"

REQUIRED:
- Provider + ALL consumers updated in SAME session
- BUILD SUCCESSFUL with ALL consumers working
- No broken code left behind
```

**Real-World Example: HardwareDetectionService → UnifiedHardwareManager**

**❌ WRONG Approach (Incomplete):**
```
1. Create UnifiedHardwareManager ✅
2. Test UnifiedHardwareManager ✅
3. Stop here ❌
Result: MainActivity still uses HardwareDetectionService (broken architecture)
```

**✅ CORRECT Approach (Complete):**
```
1. Create UnifiedHardwareManager ✅
2. Identify consumers: MainActivity, DashboardScreen, DashboardViewModel ✅
3. Update MainActivity imports + code ✅
4. Update DashboardScreen imports + code ✅
5. Update DashboardViewModel imports + code ✅
6. Delete HardwareDetectionService (now unused) ✅
7. Build and verify ALL screens working ✅
Result: Complete migration, no broken code
```

**Consumer Update Checklist:**
```
When changing provider X:

□ Searched codebase for all X usages (grep/search)
□ Listed ALL consumer files with line counts
□ Documented required changes for EACH consumer
□ Estimated time for consumer updates
□ Updated ALL consumers (no exceptions)
□ Compiled and verified (BUILD SUCCESSFUL)
□ Tested affected screens/features
□ Deleted old provider if fully replaced

If ANY checkbox is unchecked → WORK IS INCOMPLETE
```

**Time Management:**
```
Provider change time: 30 minutes
Consumer update time: 20 minutes (4 files × 5 min each)
Total time: 50 minutes

NOT ACCEPTABLE:
- Spend 30 min on provider, declare "done"

REQUIRED:
- Spend 50 min total (provider + consumers), then "done"
```

**Anti-Patterns to AVOID:**

**❌ Orphaned Consumers:**
```
New API created, old consumers still use old API
Result: Half-migrated codebase, confusion about which to use
```

**❌ Dead Code:**
```
New implementation added, old implementation never deleted
Result: Duplication, maintenance burden, unclear which is "correct"
```

**❌ "TODO: Update Consumers":**
```
Provider changed, consumers marked with TODOs for later
Result: TODOs never get done, code stays broken
```

**✅ Correct Pattern:**
```
1. Plan: "I need to change X, which affects files A, B, C (15 changes)"
2. Execute: Change X + update A + update B + update C
3. Verify: Build successful, all features working
4. Complete: No broken code, no TODOs, no dead code
```

**Integration with Other Principles:**

This principle works WITH the existing principles:
- **EXPLICIT OVER IMPLICIT:** Consumer updates must be explicit
- **READ BEFORE WRITE:** Read consumer code before updating
- **MAP BEFORE CODE:** Map all consumers before changing provider
- **VALIDATE BEFORE COMMIT:** Validate ALL consumers work after change

**Enforcement:**

Before changing ANY provider (class/API/architecture):
```
□ Have I searched for ALL consumers?
□ Have I listed ALL files that need updates?
□ Do I have time to update ALL consumers NOW?
□ Am I committed to atomic provider+consumer updates?
```

**If ANY answer is NO → Don't change the provider yet.**

After changing provider:
```
□ Have I updated ALL identified consumers?
□ Have I deleted old provider if fully replaced?
□ Does BUILD SUCCESSFUL still pass?
□ Do ALL affected features still work?
```

**If ANY answer is NO → Work is incomplete, keep going.**

---

## THE SYSTEMATIC PROCESS (Universal Workflow)

**This process applies to EVERY code generation task. EVERY. SINGLE. ONE.**

---

### STEP 1: SCOPE DEFINITION (30 seconds)

**What am I building?**
- Write 1-2 sentence description
- List success criteria (e.g., "Compiles without errors", "Passes all tests")
- Identify obvious dependencies
- **NEW:** Identify if this change affects existing code (provider change?)

**Example:**
```
Task: Generate ProductListScreen
Success: Compiles first try, displays products from ViewModel
Dependencies: ProductViewModel, Product data class, Compose Material3
Ripple Effect: No (new screen, doesn't replace existing code)
```

**Example with Ripple Effect:**
```
Task: Create UnifiedHardwareManager (replaces HardwareDetectionService)
Success: Compiles first try, all consumers updated and working
Dependencies: NfcDeviceModule, framework.devices APIs
Ripple Effect: YES
  - Consumers: MainActivity.kt, DashboardScreen.kt, DashboardViewModel.kt
  - Updates needed: 18 changes across 3 files
  - Estimated time: Provider (30 min) + Consumers (15 min) = 45 min total
```

---

### STEP 1.5: CONSUMER IMPACT ANALYSIS (2-3 minutes, IF ripple effect identified)

**Do existing files use what I'm changing?**

**Run searches:**
```bash
# If replacing class
grep -r "OldClassName" src/ | wc -l

# If changing method signature  
grep -r "methodName" src/ | wc -l

# If changing data structure
grep -r "OldDataClass" src/ | wc -l
```

**Document findings:**
```
Consumer Impact Assessment:
- Files affected: 3 (MainActivity, DashboardScreen, DashboardViewModel)
- Total changes: ~18 locations
- Types of changes: imports (3), initialization (3), method calls (8), state access (4)
- Estimated time: 15 minutes
- Required: Must be done BEFORE declaring task complete
```

**Decision:**
- If NO consumers → Proceed to Step 2
- If YES consumers → Include consumer updates in task scope, proceed to Step 2

---

### STEP 2: DEPENDENCY MAPPING (2-5 minutes)

**What do I need to know?**
- List ALL classes/interfaces this code interacts with
- List ALL APIs/frameworks this code uses
- For each dependency: identify file path

**Example:**
```
Dependencies for ProductListScreen:
1. ProductViewModel - app/viewmodels/ProductViewModel.kt
2. Product data class - app/models/Product.kt
3. Compose Material3 - Text, Card, LazyColumn APIs
4. Navigation - app/navigation/NavGraph.kt
```

---

### STEP 3: DEFINITION READING (5-10 minutes)

**Read and document. No skipping. No skimming.**

For EACH dependency:
1. Open the file
2. Read relevant classes/methods
3. Document exact names, types, signatures
4. Note any constraints or special requirements

**Example - Reading ProductViewModel.kt:**
```
ProductViewModel.kt opened

StateFlows found:
- products: StateFlow<List<Product>>           ← List of Product, not Product?
- isLoading: StateFlow<Boolean>
- errorMessage: StateFlow<String?>             ← String?, nullable

Methods found:
- loadProducts(): Unit                         ← No parameters, returns Unit
- refreshProducts(): Unit
- searchProducts(query: String): Unit          ← String parameter, not String?

No enums in this class.
```

**Example - Reading Product.kt:**
```
Product.kt opened

data class Product(
    val id: Long,                              ← Long, not Int
    val name: String,                          ← String, non-nullable
    val price: Double,                         ← Double, not Float
    val imageUrl: String?,                     ← String?, nullable
    val inStock: Boolean
)

Properties documented. No nested classes.
```

---

### STEP 4: INTERFACE MAPPING (2-3 minutes)

**How do components connect?**
- Document data flow (ViewModel → Screen → Composables)
- Note type transformations needed (e.g., Long → String for display)
- Identify potential type mismatches

**Example:**
```
Data Flow for ProductListScreen:

ViewModel                     Screen                      UI
products: List<Product>  →    val products by         →   LazyColumn {
                              viewModel.products           items(products) {
                              .collectAsState()              ProductCard(it)
                                                          }
                                                       }

Type Conversions Needed:
- Product.price (Double) → Text needs String → "${product.price}"
- Product.imageUrl (String?) → AsyncImage needs non-null → imageUrl ?: placeholderUrl

No other conversions needed.
```

---

### STEP 5: GENERATION WITH PRECISION (10-30 minutes)

**Code using ONLY information from Steps 1-4.**

Rules:
- Reference your documentation from Steps 2-4
- Copy names character-by-character (don't retype from memory)
- Use exact types documented
- Apply explicit conversions where documented
- No improvisation, no "I think it's...", no guessing

**Anti-Pattern:**
```kotlin
// Typing from memory, guessing
val items = viewModel.items.collectAsState()  // Wrong! It's "products" not "items"
Text(product.cost)                            // Wrong! It's "price" not "cost", and needs .toString()
```

**Correct Pattern:**
```kotlin
// Referencing documentation from Step 3
val products by viewModel.products.collectAsState()  // ✅ Exact name from Step 3
Text("${product.price}")                             // ✅ Exact name + explicit conversion from Step 4
```

---

### STEP 6: SELF-VALIDATION (5-10 minutes)

**Review before compile. Catch errors in review, not compilation.**

For generated code:
1. Compare every property name with Step 3 documentation
2. Compare every method call with Step 3 documentation
3. Verify every type conversion from Step 4 is applied
4. Check for any guessed names (if you didn't document it, you guessed it)

**Validation Checklist:**
```
□ Every property access verified against documentation
□ Every method call verified against documentation
□ Every type conversion applied as documented
□ No names used that weren't in documentation
□ No assumptions about nullability
□ No assumptions about types
```

---

### STEP 7: COMPILE AND VERIFY (1-2 minutes)

**The moment of truth.**

```bash
./gradlew compileDebugKotlin
```

**Expected outcome:** BUILD SUCCESSFUL

**If compilation fails:**
1. Read EXACT error message (don't guess what it means)
2. Identify which step failed:
   - Wrong property name? → Step 3 was incomplete
   - Wrong type? → Step 4 missed a conversion
   - Wrong signature? → Step 3 was incomplete
3. Go back to that step, complete it properly
4. Update your process to catch this earlier next time

**If compilation succeeds:**
✅ Process worked. Apply same process to next task.

---

### STEP 8: CONSUMER UPDATE VERIFICATION (5-15 minutes, IF ripple effect identified)

**Did I update ALL consumers?**

**If Step 1 identified consumers:**

1. **Verify each consumer updated:**
```
Consumer Checklist:
□ MainActivity.kt - Updated imports ✅
□ MainActivity.kt - Updated initialization ✅  
□ MainActivity.kt - Updated method calls ✅
□ DashboardScreen.kt - Updated imports ✅
□ DashboardScreen.kt - Updated state access ✅
□ DashboardViewModel.kt - Updated imports ✅
□ DashboardViewModel.kt - Updated initialization ✅

All consumers updated: YES
```

2. **Recompile with consumers:**
```bash
./gradlew :android-app:compileDebugKotlin
```

3. **Expected outcome:** BUILD SUCCESSFUL (with ALL consumers working)

4. **Test affected features:**
```bash
# If UI changed, install and test
./gradlew :android-app:assembleDebug
adb install -r android-app/build/outputs/apk/debug/android-app-debug.apk
adb shell am start -n com.nfsp00f33r.app/.MainActivity
# Verify screens load, no crashes
```

5. **Clean up old code:**
```
If new implementation fully replaces old:
□ Delete old provider file (if unused)
□ Remove @Deprecated markers (if migration complete)
□ Update documentation
□ Remove any migration TODOs
```

**Task ONLY complete when:**
- ✅ Provider changed
- ✅ ALL consumers updated  
- ✅ BUILD SUCCESSFUL with consumers
- ✅ Features tested and working
- ✅ Old code removed (if applicable)

**If ANY step incomplete → Task is NOT done, continue working**

---

## LESSONS FROM PHASE 3 BATCH 2

### Error Statistics:
- **Total errors:** 29 (after external tool modifications)
- **Category 1 (Type Mismatches):** 4 errors - ALL from not reading definitions
- **Category 2 (Unresolved References):** 12 errors - ALL from guessing names/scope
- **Category 3 (Nullable Safety):** 7 errors - ALL from inconsistent null handling
- **Category 4 (Method Signatures):** 6 errors - ALL from not validating APIs

### Prevention Rate:
**100% of these errors were preventable** by following the rules in this document.

### Time Cost:
- Code generation: ~2 hours
- Error fixing (systematic protocol): ~3 hours
- **Total:** 5 hours

**With these rules applied BEFORE generation:** ~2 hours (60% time savings)

---

## ENFORCEMENT PROTOCOL

### Self-Check Before Generation:
```
Before I generate ANY code, I must answer YES to ALL:

MAPPING & READING:
1. [ ] Have I READ the complete source files for ALL dependencies?
2. [ ] Have I DOCUMENTED all properties/methods with exact types?
3. [ ] Have I VERIFIED all signatures I'll call?
4. [ ] Am I generating code with EXACT names (no assumptions)?

RIPPLE EFFECT ANALYSIS:
5. [ ] Does this change replace/modify existing code?
6. [ ] If YES: Have I searched for ALL consumers?
7. [ ] If YES: Have I documented ALL required consumer updates?
8. [ ] If YES: Do I have time to update ALL consumers NOW?

SCOPE-SPECIFIC (Compose UI):
9. [ ] Have I CHECKED scope rules for remember{} states?
10. [ ] Have I VALIDATED all Compose APIs I'll use?
```

**If ANY answer is NO → STOP and complete that step first.**

### Self-Check After Generation:
```
Before I declare task COMPLETE, I must answer YES to ALL:

PROVIDER VERIFICATION:
1. [ ] Does the generated code compile (BUILD SUCCESSFUL)?
2. [ ] Have I validated all names/types against documentation?
3. [ ] Are all type conversions explicit?

CONSUMER VERIFICATION:
4. [ ] If ripple effect identified: Have I updated ALL consumers?
5. [ ] If ripple effect identified: Does code still compile with consumers?
6. [ ] If ripple effect identified: Have I tested affected features?
7. [ ] If ripple effect identified: Have I removed old code (if applicable)?

COMPLETION:
8. [ ] Is there ANY broken code left behind?
9. [ ] Is there ANY TODO related to this change?
10. [ ] Can I honestly say this task is 100% COMPLETE?
```

**If ANY answer is NO → Task is INCOMPLETE, keep working.**

---

## THE COMMITMENT

**Every code generation task, every time:**

```
I will MAP before I CODE.
I will READ before I WRITE.
I will VALIDATE before I COMMIT.
I will UPDATE CONSUMERS when I change PROVIDERS.
I will be SYSTEMATIC, not ad-hoc.
I will be PRECISE, not approximate.
I will be COMPLETE, not partial.
I will be EFFICIENT through discipline, not speed through shortcuts.
```

**Success looks like:**
- ✅ Complete mapping in 10 minutes (includes consumer analysis)
- ✅ Generation completes in 30 minutes
- ✅ Consumer updates complete in 15 minutes
- ✅ Self-validation finds all issues in 10 minutes
- ✅ **BUILD SUCCESSFUL on first compile (with ALL consumers working)**
- ✅ Total time: 65 minutes for COMPLETE working code (provider + consumers)

**Failure looks like:**
- ❌ No mapping, start coding immediately
- ❌ Generation takes 2 hours (guessing, rewriting)
- ❌ Skip consumer analysis
- ❌ Provider works, consumers broken
- ❌ Skip self-validation
- ❌ **20 compilation errors**
- ❌ 3 hours fixing errors
- ❌ Task declared "done" but consumers still broken
- ❌ Total time: 5+ hours for INCOMPLETE code

**The math is simple:** Systematic + Complete is faster than Fast + Broken.

---

## ENFORCEMENT

**This is not optional. This is how code is generated. Period.**

Before starting ANY code generation:
```
PLANNING:
□ Do I have a written map of what I'm building?
□ Have I read ALL dependency definitions?
□ Have I documented ALL names/types/signatures I'll use?
□ Can I generate this code WITHOUT guessing anything?

RIPPLE EFFECT:
□ Does this change affect existing code?
□ If YES: Have I identified ALL consumers?
□ If YES: Have I planned ALL consumer updates?
□ If YES: Do I have time for provider + consumer updates NOW?
```

**If ANY answer is NO → STOP. Complete that step.**

After generating provider code:
```
PROVIDER VALIDATION:
□ Have I reviewed every property name against documentation?
□ Have I reviewed every method call against documentation?
□ Have I verified all type conversions are applied?
□ Am I confident this compiles first try?

CONSUMER VALIDATION (if ripple effect):
□ Have I updated ALL identified consumers?
□ Have I verified consumers still compile?
□ Have I tested affected features?
□ Have I removed old code if fully replaced?
```

**If ANY answer is NO → FIX IT NOW before declaring complete.**

---

## CONTINUOUS IMPROVEMENT

**Every compilation error is a process failure.**

When errors occur:
1. Document the error type
2. Identify which step failed (Map? Read? Generate? Validate?)
3. Update process to catch this error type earlier
4. Apply improved process to next task

**Goal:** Reduce error rate to zero over time through systematic improvement.

---

**Status:** Active Universal Law  
**Applies To:** ALL code generation (UI, APIs, models, tests, migrations, refactoring)  
**Violations:** Cause compilation errors, waste time, reduce code quality  
**Compliance:** Mandatory for all production code

**Remember:** Fast code that doesn't compile wastes more time than slow code that works first try.
