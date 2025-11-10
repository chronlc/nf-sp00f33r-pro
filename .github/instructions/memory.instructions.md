---
applyTo: '**'
description: Workspace-specific AI memory for this project
lastOptimized: '2025-11-10T10:57:10.028289+00:00'
entryCount: 2
optimizationVersion: 2
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
- **2025-11-10 02:56:** Multi-AID EMV storage: Currently uses first AID only (aids.first()) in executeEmvTransaction, but database schema supports isolated tag storage per AID via aidsData: List<AidData>. Each AID entry can have separate PDOL, AFL, and parsed tags. Current limitation: all EMV tags stored in single allEmvTags Map per session. Future: need to implement per-AID tag isolation and loop through multiple AIDs instead of selecting first one.