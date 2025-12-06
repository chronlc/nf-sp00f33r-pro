---
applyTo: '**'
description: Professional Git Commit Message Standards - Code-Specific Guidelines
priority: high
enforceStrict: true
created: '2025-12-06'
---

# GIT COMMIT MESSAGE STANDARDS

**Purpose:** Maintain clear, professional, code-specific commit history for production codebase.

---

## COMMIT MESSAGE STRUCTURE

```
[TYPE] Brief imperative summary (max 50 chars)

Detailed technical description explaining WHAT changed, WHY it was 
necessary, and HOW it was implemented. Wrap at 72 characters.

Technical Details:
- Modified: ClassName.kt, functionName()
- Added: newFunction() with specific algorithm/pattern
- Changed: dataFlow from X to Y
- Removed: deprecatedFunction()
- Pattern: Design pattern or architectural approach used

Root Cause (for FIX):
- Specific bug or issue identified
- Code path that caused the problem
- Line numbers or stack trace reference

Implementation Details:
- Key code changes with method/class names
- Threading model (Dispatchers.IO, Main, etc.)
- Data flow changes (Flow, LiveData, StateFlow, etc.)
- Database schema changes
- API contract changes

Impact:
- Performance: Quantify if possible (20% faster, 50% less memory)
- Behavior: What user-visible changes occur
- Breaking changes: List deprecated/removed APIs
- Dependencies: New libraries or version updates

Testing:
- Build: ./gradlew compileDebugKotlin - SUCCESS/FAIL
- Unit tests: Pass/Fail count, coverage change
- Integration tests: Specific scenarios tested
- Manual verification: What was tested and observed

Related:
- Issue: #123
- Depends on: commit hash or PR number
- Related commits: Reference related work
```

---

## COMMIT TYPES

| Type | Usage | Example |
|------|-------|---------|
| **FEAT** | New feature or functionality | Add biometric authentication to CardReadingViewModel |
| **FIX** | Bug fix | Fix null pointer in EmvTlvParser.parseTag() |
| **REFACTOR** | Code restructuring (no behavior change) | Extract EMV session logic into separate use case |
| **PERF** | Performance improvement | Optimize database query with index on sessionId |
| **DOCS** | Documentation only | Update CHANGELOG.md with Flow migration details |
| **TEST** | Test additions or changes | Add integration tests for database Flow observation |
| **BUILD** | Build system or dependencies | Upgrade Room database to version 2.6.0 |
| **CI** | CI/CD pipeline changes | Add GitHub Actions workflow for automated builds |
| **STYLE** | Code style/formatting (no logic change) | Apply ktlint formatting to DatabaseViewModel.kt |
| **CHORE** | Maintenance tasks | Clean up unused imports in cardreading package |

---

## CODE-SPECIFIC REQUIREMENTS

### 1. **Always Include Class/Function Names**
```
❌ BAD: "Fixed database issue"
✅ GOOD: "Fix race condition in DatabaseViewModel.observeDatabaseChanges()"
```

### 2. **Specify Technical Approach**
```
❌ BAD: "Improved database performance"
✅ GOOD: "Add composite index on (cardUid, scanTimestamp) in EmvCardSessionEntity"
```

### 3. **Name Design Patterns Used**
```
❌ BAD: "Refactored code"
✅ GOOD: "Refactor to Repository pattern - Add EmvSessionRepository between ViewModel and DAO"
```

### 4. **Quantify Changes**
```
❌ BAD: "Made it faster"
✅ GOOD: "Reduce database query time from 450ms to 120ms by using Flow instead of getAllSessions()"
```

### 5. **Reference Android/Kotlin APIs**
```
✅ GOOD: "Migrate from LiveData to StateFlow in CardReadingViewModel"
✅ GOOD: "Replace viewModelScope.launch with supervisorScope for error isolation"
✅ GOOD: "Add @Transaction annotation to deleteAndInsert() in DAO"
```

---

## EXAMPLE COMMITS (PROFESSIONAL)

### Example 1: Feature Addition
```
[FEAT] Add real-time Flow observation to DatabaseViewModel

Implement reactive database synchronization using Room's Flow API
to automatically update UI when CardReadingViewModel inserts new
EMV sessions. Replaces one-time init{} load with continuous observer.

Technical Details:
- Modified: DatabaseViewModel.kt
- Added: observeDatabaseChanges() using viewModelScope.launch(Dispatchers.IO)
- Changed: init{} calls observeDatabaseChanges() instead of refreshData()
- Pattern: Observer pattern via Kotlin Flow with collectLatest{}
- Threading: Flow collection on IO dispatcher, UI updates on Main
- Removed: Manual refreshData() calls from deleteCard(), clearAllCards()

Implementation:
- emvSessionDao.getAllSessionsFlow() returns Flow<List<EmvCardSessionEntity>>
- collectLatest{} ensures only latest emission processed (backpressure handling)
- Entity to CardProfile conversion happens on IO thread
- UI state (cardProfiles, filteredCards) updated atomically on Main thread

Impact:
- Cards appear in Database Screen instantly after Read Screen scan completes
- Delete/clear operations refresh UI automatically (no manual refresh)
- Eliminates stale data issue where Database Screen showed old state
- Zero user action required for data synchronization

Testing:
- Build: ./gradlew compileDebugKotlin - SUCCESSFUL
- Manual: Scanned test card, switched to Database Screen - card visible immediately
- Manual: Deleted card - UI updated without navigation
- No regressions in existing card display logic

Related:
- Issue: Database Screen not showing newly scanned cards
- DAO: EmvCardSessionDao.getAllSessionsFlow() added in commit abc1234
```

### Example 2: Bug Fix
```
[FIX] Prevent ConcurrentModificationException in SessionScanData.allTags

Add synchronized block around allTags Map mutations to prevent race
condition when multiple coroutines parse EMV tags concurrently during
GPO and READ RECORD phases.

Technical Details:
- Modified: CardReadingViewModel.kt, SessionScanData.allTags
- Changed: allTags from HashMap to ConcurrentHashMap
- Added: @Synchronized annotation to addEnrichedTag() method
- Pattern: Thread-safe collections with synchronized mutations

Root Cause:
- parseGpoResponse() and parseRecordResponse() both modify allTags Map
- Both run on Dispatchers.IO thread pool (multiple threads)
- ConcurrentModificationException thrown at: allTags.put(key, value)
- Stack trace: CardReadingViewModel.kt:2156

Implementation:
- Replace: private val allTags = HashMap<String, EnrichedTagData>()
- With: private val allTags = ConcurrentHashMap<String, EnrichedTagData>()
- Add synchronized wrapper for complex put operations:
  @Synchronized fun addEnrichedTag(key: String, value: EnrichedTagData) {
      allTags[key] = value
  }

Impact:
- Eliminates random crashes during multi-AID card scans
- No performance degradation (ConcurrentHashMap optimized for concurrent reads)
- Maintains existing tag deduplication logic with @occurrence suffix

Testing:
- Build: ./gradlew compileDebugKotlin - SUCCESSFUL
- Unit: Added testConcurrentTagInsertion() with 10 parallel writers
- Integration: Scanned 5 multi-AID cards - zero crashes
- Regression: Verified single-AID cards still work correctly
```

### Example 3: Refactoring
```
[REFACTOR] Extract EMV session persistence to repository layer

Introduce EmvSessionRepository as abstraction between ViewModels and
EmvSessionDao to centralize business logic and enable easier testing.
Follows Clean Architecture repository pattern.

Technical Details:
- Added: EmvSessionRepository.kt (92 lines)
- Modified: CardReadingViewModel.kt, DatabaseViewModel.kt, DashboardViewModel.kt
- Changed: Direct DAO calls replaced with repository methods
- Pattern: Repository pattern with suspend functions and Flow
- Dependency Injection: Repository provided via lazy delegate

Implementation:
class EmvSessionRepository(private val dao: EmvCardSessionDao) {
    // Observable Flow for real-time updates
    fun observeAllSessions(): Flow<List<EmvCardSessionEntity>> = 
        dao.getAllSessionsFlow()
    
    // Business logic: Save with validation
    suspend fun saveSession(entity: EmvCardSessionEntity): Result<Long> {
        return try {
            if (entity.pan.isNullOrEmpty()) {
                return Result.failure(InvalidPanException())
            }
            val id = dao.insert(entity)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

Changes by File:
- CardReadingViewModel.kt:
  - Replace: emvSessionDao.insert(entity)
  - With: repository.saveSession(entity).getOrThrow()
  
- DatabaseViewModel.kt:
  - Replace: emvSessionDao.getAllSessionsFlow()
  - With: repository.observeAllSessions()

Impact:
- Centralizes validation logic (DRY principle)
- Enables repository layer unit testing without Room
- ViewModels no longer depend on Room directly
- Easier to swap DAO implementation (e.g., remote API)

Testing:
- Build: ./gradlew compileDebugKotlin - SUCCESSFUL
- Unit: Added EmvSessionRepositoryTest with MockDao (100% coverage)
- Integration: All 3 ViewModels tested with real repository
- Regression: Verified all screens show cards correctly
```

---

## ANTI-PATTERNS TO AVOID

### ❌ Vague Descriptions
```
BAD: "Fixed bug"
BAD: "Updated code"
BAD: "Improvements"
BAD: "Changed stuff"
```

### ❌ Missing Technical Details
```
BAD: [FIX] Database not working
     (No class names, no root cause, no implementation details)
```

### ❌ Non-Imperative Mood
```
BAD: "Fixed the database issue"  (past tense)
BAD: "Fixes database issue"      (present continuous)
GOOD: "Fix database race condition in observeDatabaseChanges()"
```

### ❌ Multiple Unrelated Changes
```
BAD: [FEAT] Add Flow observation, fix null pointer, update README
     (Should be 3 separate commits)
```

### ❌ No Testing Information
```
BAD: [Commit with no mention of build status or testing]
```

---

## COMMIT CHECKLIST

Before committing, verify:

```
□ Type prefix matches change category (FEAT/FIX/REFACTOR/etc)
□ Summary is imperative mood, under 50 chars
□ Body explains WHAT/WHY/HOW with technical details
□ All modified class/function names listed
□ Design patterns or architectural approaches named
□ Threading model specified (if applicable)
□ Performance impact quantified (if applicable)
□ Build status included (SUCCESS/FAIL)
□ Testing approach described
□ Breaking changes highlighted
□ Related issues/commits referenced
```

---

## CONFIGURATION

To use the commit template automatically:

```bash
# Set commit template globally
git config --global commit.template /home/user/DEVCoDE/FINALS/nf-sp00f33r/.gitmessage

# Or project-specific
cd /home/user/DEVCoDE/FINALS/nf-sp00f33r
git config commit.template .gitmessage
```

Now when you run `git commit`, the template will open in your editor.

---

**Status:** Production Standard  
**Authority:** Universal Law - ALL commits must follow this format  
**Last Updated:** 2025-12-06

