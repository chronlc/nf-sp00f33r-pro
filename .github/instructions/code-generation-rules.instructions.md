---
applyTo: '**'
description: Code Generation Rules - Universal Laws for Production Code
priority: critical
enforceStrict: true
created: '2025-10-07'
---

# CODE GENERATION RULES (Universal Laws)

**Root cause of errors:** GUESSING instead of READING. ASSUMING instead of VALIDATING.

Applies to ALL code: UI, business logic, models, tests, APIs, migrations, refactoring.

---

## THE SYSTEMATIC PROCESS

**STEP 1: SCOPE**
- 1-2 sentence description, success criteria, dependencies
- Identify ripple effect (does this change existing code?)
- Create TODO list, mark task in-progress

**STEP 2: CONSUMER IMPACT (IF ripple effect)**
- Search for ALL consumers: grep -r "ClassName" src/ --include="*.kt"
- Document: files affected, lines to change, risk level
- Gate check: Have time to update ALL consumers NOW? If NO → STOP

**STEP 3: DEPENDENCY MAPPING**
- List ALL classes/interfaces/APIs this code uses
- Identify file paths
- Cross-reference with Step 2 findings

**STEP 4: READ & VERIFY NAMING**
- Read EVERY dependency file completely
- Document exact names, types, signatures
- Verify naming compliance (kebab-case files, PascalCase classes, camelCase functions)
- If violations found → STOP and plan refactor

**STEP 5: INTERFACE MAPPING**
- Document data flow (ViewModel → Screen → UI)
- Note type transformations needed
- Identify mismatches

**STEP 6: GENERATE WITH PRECISION**

Rules:
- Use ONLY information from Steps 1-5
- Copy names exactly (no guessing)
- Apply explicit type conversions
- **NO HARDCODED DATA, NO SIMULATION**
- **ADD COMPREHENSIVE COMMENTS**:
  - KDoc for every function (what, why, data source)
  - Inline comments explaining data origin
  - Every StateFlow must note what real data it represents
  - Every error handler includes ModMainDebug logging

Anti-patterns to NEVER use:
```kotlin
❌ fun simulateCardRead() { ... }                    // NO mock/simulate functions
❌ _status.value = "Available"                       // NO hardcoded values
❌ _data.value = CardSession(pan="123456...")        // NO fake data
✅ _status.value = actualDeviceStatus               // YES - real device state
✅ val reads = emvDatabase.getRecent()              // YES - real database
✅ ModMainDebug.debugLog(...)                       // YES - logging
```

**STEP 7: SELF-VALIDATE**
- Compare every name with Step 4 documentation
- Verify all type conversions applied
- Search for simulate/mock functions → DELETE ALL
- Check: ALL UI data from real sources? YES? → Proceed

**STEP 7.5: FUNCTIONALITY PRESERVATION (IF code simplified)**
- What was original functionality?
- What was removed/changed?
- Can all original use cases still be performed?
- If removed: Document TODO for when it returns
- Gate check: Can't declare complete if functionality lost

**STEP 8: COMPILE**
```bash
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**STEP 9: UPDATE CONSUMERS (IF ripple effect)**
- Update ALL identified consumers from Step 2
- Recompile with consumers
- Test affected features
- Clean up old code if replaced

**STEP 10: CHANGELOG**
- Add entry at TOP of CHANGELOG.md
- Format: `[Date] - [Feature/Fix]: Description`
- Include files changed, scope, breaking changes

**STEP 11: COMMIT**
```bash
git add .
git commit -m "[SCOPE] Brief description

- File 1: Change summary
- File 2: Change summary"
git push origin master
```

**STEP 12: REMEMBER MCP**
- Document in `.github/instructions/memory.instructions.md`
- Format: `[DATE TIME] - [TASK-NAME]: Completed - Commit: [hash]`

---

## PRE-GENERATION CHECKLIST

Before writing ANY code:

```
0. [ ] Created TODO list, marked task in-progress?
1. [ ] Defined scope clearly?
2. [ ] Identified ripple effects?
3. [ ] Checked naming compliance in existing code?
4. [ ] READ all dependency files completely?
5. [ ] DOCUMENTED all names/types/signatures exactly?
6. [ ] Can I generate this WITHOUT GUESSING anything?
7. [ ] If ripple effect: Identified ALL consumers?
8. [ ] If ripple effect: Have time to update ALL NOW?
9. [ ] Will ALL UI data come from real sources?
10. [ ] Will I use ZERO hardcoded mock values?
```

If ANY = NO → STOP and complete that step first.

---

## POST-GENERATION CHECKLIST

Before declaring COMPLETE:

```
□ All names verified against documentation
□ All type conversions applied
□ NO simulate/mock functions anywhere
□ Code compiles (BUILD SUCCESSFUL)
□ All consumers updated (if ripple effect)
□ Code still compiles with consumers
□ Features tested
□ Changes committed with proper message
□ Changes pushed to remote
□ Memory updated
□ No outstanding TODOs for this task
```

If ANY = NO → Task is INCOMPLETE, keep working.

---

## ENFORCEMENT

**Every compilation error is a process failure.**

When errors occur:
1. Identify which step failed (Map? Read? Generate? Validate?)
2. Update process to catch this error earlier
3. Apply improved process to next task
4. Goal: Reduce error rate to zero

**Success:** Complete mapping (10 min) → Generation (30 min) → Validation (10 min) → Consumers (15 min) → BUILD SUCCESS (first try)

**Failure:** No mapping → Guess names → Provider works → Consumers broken → 20 errors → Task incomplete

**The math:** Systematic is faster than broken.

---

**Status:** Production Standard  
**Authority:** Universal Law - NO EXCEPTIONS  
**Last Updated:** 2025-11-05
