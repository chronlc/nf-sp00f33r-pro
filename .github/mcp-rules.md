# Code Generation Rules - MCP Edition

**Core Principle:** PRECISION OVER SPEED. VALIDATION OVER ASSUMPTION. COMPLETE OVER PARTIAL.

Root cause of errors: GUESSING vs READING, ASSUMING vs VALIDATING, IMPROVISING vs PLANNING, PARTIAL vs COMPLETE.

## 7-Step Systematic Process

**STEP 1: SCOPE (30s)** - Define what building, success criteria, dependencies, check ripple effect

**STEP 2: CONSUMER IMPACT (2-3m if ripple)** - `grep -r "OldClass" src/`, document all affected files, commit to updating ALL

**STEP 3: MAPPING (2-5m)** - List ALL classes/APIs used, document file paths

**STEP 4: READING (5-10m)** - READ source files (not grep), document exact property names/types/signatures, everything

**STEP 5: INTERFACE (2-3m)** - Document data flow, type conversions needed, potential mismatches

**STEP 6: GENERATION (10-30m)** - Use ONLY documented info, copy names exactly, explicit conversions, no guessing

**STEP 7: VALIDATION (5-10m)** - Self-review vs docs, compile, if ripple: update ALL consumers, recompile, test, cleanup

## 5 Universal Principles

**1. EXPLICIT OVER IMPLICIT** - Make all conversions explicit, no hoping compiler converts

**2. READ BEFORE WRITE** - Never use property/method without reading definition, "should have X" → NO, read and verify

**3. MAP BEFORE CODE** - Write plan first, list dependencies, document names/types, can't write map → not ready

**4. VALIDATE BEFORE COMMIT** - Self-review line-by-line, find errors in review (seconds) not compile (minutes)

**5. RIPPLE EFFECT** - Changing provider = MUST update ALL consumers, atomic operation, half-updated = broken, update everything or nothing

## Ripple Protocol

**BEFORE:** Search consumers, document files, plan updates, estimate time

**AFTER:** Update provider → update ALL consumers → build → test → cleanup → COMPLETE

**NOT ALLOWED:** "later", "test first", TODOs

**REQUIRED:** Provider + ALL consumers same session, BUILD SUCCESSFUL, no broken code

## Checklists

**PRE-GEN:**
- □ READ all dependencies? □ DOCUMENTED exact types? □ VERIFIED signatures? □ No assumptions?
- □ Ripple effect? □ Found consumers? □ Planned updates? □ Have time NOW?

**POST-GEN:**
- □ Compiles? □ Validated vs docs? □ Explicit conversions?
- □ Updated consumers? □ Still compiles? □ Tested? □ Cleaned up?
- □ No broken code? □ No TODOs? □ 100% COMPLETE?

**ANY "NO" → STOP and fix**

## The Commitment

MAP before CODE. READ before WRITE. VALIDATE before COMMIT. UPDATE CONSUMERS with PROVIDERS. BE COMPLETE not partial.

**Success:** 65min, BUILD SUCCESSFUL first try, all consumers work
**Failure:** 5+ hours, errors, broken consumers, incomplete

Systematic + Complete beats Fast + Broken.
