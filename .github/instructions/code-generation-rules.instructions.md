```instructions
# Code Generation Rules - Copilot Edition

**Core Principle:** PRECISION OVER SPEED. VALIDATION OVER ASSUMPTION. COMPLETE OVER PARTIAL.

## The Problem
Compilation errors come from:
- **GUESSING** instead of **READING**
- **ASSUMING** instead of **VALIDATING**  
- **IMPROVISING** instead of **PLANNING**
- **PARTIAL** instead of **COMPLETE** (broken consumers)

## The Solution: 7-Step Process

### STEP 1: SCOPE DEFINITION (30 sec)
- Write 1-2 sentence task description
- List success criteria
- Identify obvious dependencies
- **Check ripple effect:** Does this change existing code?

### STEP 2: CONSUMER IMPACT ANALYSIS (2-3 min, IF ripple effect)
```bash
# Search for all files using what you're changing
grep -r "OldClassName" src/ | wc -l
```
- Document ALL affected files
- Estimate update time
- **Commit:** Must update ALL consumers before task complete

### STEP 3: DEPENDENCY MAPPING (2-5 min)
- List ALL classes/APIs this code uses
- Document file paths for each

### STEP 4: DEFINITION READING (5-10 min)
**READ source files. Don't grep, don't search - READ.**
- For each class: exact property names, types, nullability
- For each method: exact signatures, parameters, return types
- **Document everything** - if it's not written, you'll guess it wrong

### STEP 5: INTERFACE MAPPING (2-3 min)
- Document data flow between components
- Note type conversions needed (Long→String, etc.)
- Identify potential type mismatches

### STEP 6: GENERATION WITH PRECISION (10-30 min)
**Use ONLY documented information from Steps 3-5**
- Copy names character-by-character from docs
- Use exact types
- Apply explicit conversions
- No guessing, no "I think it's..."

### STEP 7: VALIDATION (5-10 min)
**Self-review BEFORE compile:**
- Compare every property name vs docs
- Compare every method call vs docs
- Verify all type conversions applied
- Check for guessed names

**Then compile:**
```bash
./gradlew compileDebugKotlin
```

**Expected:** BUILD SUCCESSFUL first try

**If ripple effect - Update ALL consumers immediately:**
- Update imports, calls, state access in ALL affected files
- Recompile with consumers
- Test affected features
- Remove old code if replaced
- **Task NOT complete until ALL consumers work**

## 5 Universal Principles

### 1. EXPLICIT OVER IMPLICIT
- If API expects String, pass String (not Long hoping for conversion)
- If method needs non-null, provide non-null (not nullable hoping)
- Make ALL conversions explicit

### 2. READ BEFORE WRITE
- Never access property without reading class definition
- Never call method without reading signature
- "It should have X" → NO. Read. Verify. Use.

### 3. MAP BEFORE CODE
- Write plan before generating
- List ALL dependencies
- Document ALL names/types/signatures
- Can't write the map? Not ready to code.

### 4. VALIDATE BEFORE COMMIT
- Self-review line-by-line
- Find errors in review (seconds) not compilation (minutes)

### 5. RIPPLE EFFECT (Consumer Impact)
- Changing provider? MUST update ALL consumers
- Provider + consumers = ATOMIC operation
- Half-updated code = broken code
- Either update everything or update nothing

## Ripple Effect Protocol

**When changing ANY provider (class/API/architecture):**

**BEFORE change:**
```bash
# Find ALL consumers
grep -r "OldClassName" src/
```
- Document ALL affected files
- Plan ALL required updates
- Estimate time: Provider + Consumers

**AFTER change:**
1. Update provider ✅
2. Update ALL consumers immediately ✅
3. Build with consumers ✅
4. Test affected features ✅
5. Delete old code if replaced ✅

**NOT ALLOWED:**
- "I'll update consumers later"
- "Let's test provider first"
- TODOs for consumer updates

**REQUIRED:**
- Provider + ALL consumers in SAME session
- BUILD SUCCESSFUL with ALL working
- No broken code left behind

## Pre-Generation Checklist
```
MAPPING:
□ READ all dependency source files?
□ DOCUMENTED all properties/methods with exact types?
□ VERIFIED all signatures I'll call?
□ Generating with EXACT names (no assumptions)?

RIPPLE EFFECT:
□ Does this change/replace existing code?
□ If YES: Searched for ALL consumers?
□ If YES: Documented ALL required updates?
□ If YES: Have time to update ALL consumers NOW?
```

**ANY "NO" → STOP and complete that step**

## Post-Generation Checklist
```
PROVIDER:
□ Code compiles (BUILD SUCCESSFUL)?
□ Validated all names/types against docs?
□ All type conversions explicit?

CONSUMERS (if ripple effect):
□ Updated ALL identified consumers?
□ Code still compiles with consumers?
□ Tested affected features?
□ Removed old code if replaced?

COMPLETION:
□ ANY broken code left behind? (Must be NO)
□ ANY TODOs related to this change? (Must be NO)
□ Task 100% COMPLETE? (Must be YES)
```

**ANY "NO" → Task INCOMPLETE, keep working**

## The Commitment

```
I will MAP before I CODE.
I will READ before I WRITE.
I will VALIDATE before I COMMIT.
I will UPDATE CONSUMERS when I change PROVIDERS.
I will be COMPLETE, not partial.
```

**Success:** 65 min for COMPLETE working code (provider + consumers), BUILD SUCCESSFUL first try

**Failure:** 5+ hours fixing errors, broken consumers, incomplete work

**The math:** Systematic + Complete is faster than Fast + Broken.
```
