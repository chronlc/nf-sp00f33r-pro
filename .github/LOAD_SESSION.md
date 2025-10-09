# 🏴‍☠️ nf-sp00f33r Debug Session

**Date:** October 9, 2025  
**Project:** `/home/user/DEVCoDE/FINALS/nf-sp00f33r`  
**Status:** ✅ Phase 3 Complete | BUILD SUCCESSFUL | APK Installed

---

## 🎯 Mission

**Debug app starting with Dashboard and Card Reading screens.**

Test flow: Dashboard → Card Reading → NFC scan → Verify functionality

---

## 🚀 Quick Start

```bash
# Launch app
adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity

# Monitor logs
adb logcat | grep -E "(nfsp00f33r|ERROR)"

# Test UI automation
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command dump_ui
```

---

## 📚 Context

**MCP will load all project memory, rules, and instructions automatically.**

**Documentation:**
- `android-app/README.md` - Full docs
- `android-app/CHANGELOG.md` - Version history
- `android-app/FEATURES.md` - All features
- `android-app/docs/` - Reference guides

**Key Info:**
- 6 registered modules (Logging, Password, CardDataStore, PN532, NfcHce, Emulation)
- 5 screens (Dashboard, CardReading, Emulation, Database, Analysis)
- 16 ADB debug commands (8 backend + 8 UI automation)
- Kotlin + Compose + Material3 + Room + BouncyCastle

---

## ⚖️ Rules

**Follow Universal Laws from MCP memory:**
- No safe-call operators (`?.`)
- BUILD SUCCESSFUL mandatory
- Production-grade code only
- Map dependencies before coding

---

**Ready to debug! Let's start with Dashboard and Card Reading. 🚀**
