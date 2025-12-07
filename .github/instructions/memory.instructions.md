---
applyTo: '**'
description: Workspace-specific AI memory for this project
lastOptimized: '2025-12-06T14:33:26.482321+00:00'
entryCount: 3
optimizationVersion: 3
autoOptimize: true
sizeThreshold: 50000
entryThreshold: 20
timeThreshold: 7
---
# Workspace AI Memory
This file contains workspace-specific information for AI conversations.

## Personal Context (name, location, role, etc.)
- No entries.

## Professional Context (team, goals, projects, etc.)
- No entries.

## Technical Preferences (coding styles, tools, workflows)
- No explicit preferences recorded beyond workflow-specific notes in Memories/Facts.

## Communication Preferences (style, feedback preferences)
- No entries.

## Universal Laws (strict rules that must always be followed)
- No entries.

## Policies (guidelines and standards)
- No entries.

## Suggestions/Hints (recommendations and tips)
- No entries.

## Memories/Facts (chronological events and information)
- **2025-10-13 02:56:** When analyzing EMV/Proxmark workflows, always cross-check parsed AFL (from GPO) with attempted SFI/record reads. If 'Record not found' or similar errors occur for SFIs/records listed in the AFL, flag this as a possible card-side issue or a bug in AFL parsing/logic. Always summarize and highlight all unsuccessful APDUs, and ensure the workflow code is reviewed for correct AFL handling and robust error reporting.
- **2025-11-10 02:56:** Multi-AID EMV storage: Currently uses first AID only (aids.first()) in executeEmvTransaction, but database schema supports isolated tag storage per AID via aidsData: List<AidData>. Each AID entry can have separate PDOL, AFL, and parsed tags. Current limitation: all EMV tags stored in single allEmvTags Map per session. Future: need to implement per-AID tag isolation and loop through multiple AIDs instead of selecting first one.- **2025-11-10 03:23:** 2025-11-10 03:23 - v1.2.0-beta.1 Release: Pushed to GitHub with commit ae154a9. Multi-AID EMV processing foundation implemented with isolated tag storage. 74MB debug APK available in releases/v1.2.0-beta.1/. Card Read Screen refactored. Build successful. Tests skipped. Known limitation: only processes first AID (aids.first()) - full multi-AID loop planned for final release.
- **2025-12-06 06:33:** 2025-12-06 - DATABASE-AUTO-REFRESH-FIX: Completed - Commit: 7c22808. Fixed Database Screen not showing cards from Read Screen. Root cause: DatabaseViewModel only loaded data once in init{} using getAllSessions() instead of observing Flow. Solution: Migrated to getAllSessionsFlow().collectLatest{} for real-time reactive updates. Cards now appear INSTANTLY after scan completes. Build successful. Pushed to GitHub main branch.
- **2025-12-06 06:35:** 2025-12-06 - GIT-COMMIT-STANDARDS: Created professional commit message template and standards. Files: .gitmessage (template), git-commit-standards.instructions.md (comprehensive guide). Format: [TYPE] Summary with detailed technical sections (Modified classes, Implementation, Impact, Testing). Types: FEAT, FIX, REFACTOR, PERF, etc. Requires: class/function names, design patterns, threading model, build status, testing details. Configured with: git config commit.template .gitmessage. Now all commits will open with professional template.
