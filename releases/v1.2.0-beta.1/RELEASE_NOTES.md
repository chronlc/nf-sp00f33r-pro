# nf-sp00f33r v1.2.0-beta.1 Release Notes

**Release Date:** November 10, 2025  
**Status:** 🔴 Beta - Ready for Testing  
**Build:** Debug APK (74 MB)  
**Commit:** ae154a9

---

## 🎯 What's New

### Multi-AID EMV Processing
- ✅ Detect multiple AIDs on single card (VISA + Mastercard co-branded support)
- ✅ Isolated tag storage per AID via `aidsData: List<AidData>`
- ✅ Foundation for per-AID tag comparison and attack surface analysis
- ⚠️ **Current Limitation:** Only processes first AID (aids.first()) - multi-AID loop coming in next version

### Card Read Screen Improvements
- ✅ Enhanced UI state management in `CardReadingScreen.kt`
- ✅ Improved data flow from `CardReadingViewModel`
- ✅ Better error handling and user feedback
- ✅ Real-time hardware status monitoring

### Database & Storage Enhancements
- ✅ `EmvCardSessionEntity` now supports per-AID data storage
- ✅ `PmSessionAdapter` intelligent mapping with card brand detection
- ✅ `EmvSessionExporter` for data export with per-AID isolation
- ✅ Complete APDU log with phase categorization

### Testing & Validation
- ✅ Added `EmvTlvParserAflTest` for AFL record validation
- ✅ Improved record read error detection
- ✅ Cross-validation between AFL and SFI/record attempts

---

## 📦 Installation

### Prerequisites
- Android Device or Emulator (Android 10+)
- USB Debugging enabled
- ADB installed on development machine

### Install APK
```bash
adb install -r nf-sp00f33r-v1.2.0-beta.1-debug.apk
```

### Launch App
```bash
adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity
```

---

## 🧪 Testing Focus Areas

### Priority 1: Card Scanning
- [ ] Scan single-AID cards (VISA, Mastercard, AMEX)
- [ ] Verify PAN, expiry, cardholder name extraction
- [ ] Check NFC reader initialization
- [ ] Verify Bluetooth PN532 connection

### Priority 2: Multi-AID Detection
- [ ] Test with co-branded cards (VISA + MC)
- [ ] Verify all AIDs are discovered in PPSE
- [ ] Check `aidsData` list population in database
- [ ] Verify AFL and PDOL per AID are stored

### Priority 3: EMV Workflow
- [ ] GPO (Get Processing Options) execution
- [ ] AFL record reading completion
- [ ] ARQC/TC generation
- [ ] Complete transaction flow to cryptogram

### Priority 4: Data Storage
- [ ] Database persistence after app restart
- [ ] JSON serialization/deserialization of EMV tags
- [ ] Export functionality (if available in UI)
- [ ] Tag data integrity after retrieval

---

## 🐛 Known Issues

### Current Limitations
1. **Multi-AID Processing:** Only first AID processed
   - Impact: Can't test full multi-AID workflow yet
   - Workaround: Use single-AID cards for testing
   - Fix: Coming in v1.2.0 final

2. **Test Files:** 80+ unresolved references in legacy tests
   - Impact: Tests skip in build
   - Workaround: Build with `-x test` flag
   - Fix: Updating test suite in next sprint

3. **Release Build Lint Errors:** `backup_rules.xml` configuration
   - Impact: Can't generate release APK yet
   - Workaround: Use debug APK for now
   - Fix: Simple XML fix, will be done before v1.2.0 final

### Possible Issues to Report
- [ ] NFC timeout after card removal
- [ ] PN532 connection instability
- [ ] Memory leaks with large APDU logs
- [ ] UI freezing during long EMV workflows
- [ ] Bluetooth disconnection handling

---

## 📊 Build Details

```
Gradle:        8.4
Kotlin:        1.9.10
JDK:           17.0.16
Android API:   Latest

Modules Built: ✅
- android-app main source
- PmEmvReader (1,141 lines)
- PmSessionAdapter (454 lines)
- EmvTlvParser (Singleton)
- Database layer (Room)

Tests:         ⏭️ Skipped (legacy files updating)
APK Size:      74 MB
Build Time:    8 seconds
Status:        BUILD SUCCESSFUL
```

---

## 🔄 From Previous Release (v1.1.0)

### Changes Since v1.1.0
- 14 files modified
- 2 files deleted (consolidated memory instructions)
- 1 new test file added
- 871 insertions, 1,374 deletions

### Architecture
- ViewModel: 980 lines (77% reduction from 4,268)
- Clean modular design maintained
- PmEmvReader + PmSessionAdapter pattern working well
- Database integration stable

---

## 💬 Feedback & Issues

### How to Report Issues
1. Test the features above
2. Note exact steps to reproduce
3. Check logcat output: `adb logcat | grep -E "CardReading|PmEmvReader|EmvTlv"`
4. Post issue to GitHub: https://github.com/chronlc/nf-sp00f33r-pro/issues

### Beta Testers
Special thanks to beta testers. Your feedback drives the roadmap!

---

## 🚀 Next Steps (v1.2.0 Final)

- [ ] Implement true multi-AID loop (process all AIDs, not just first)
- [ ] Add per-AID tag isolation in UI display
- [ ] Fix release build lint errors
- [ ] Update & enable full test suite
- [ ] Performance optimization for large APDU logs
- [ ] Add export functionality to UI

---

## 📝 Commit History

```
ae154a9 feat: Card Read Screen improvements & multi-AID EMV processing
3bfee85 Merge remote-tracking branch 'origin/main'
8fd7b70 feat: implement multi-AID EMV processing with isolated tag storage per AID
e4adcf6 chore: add releases directory with v1.1.0 artifacts
3e6cd05 (tag: v1.1.0) release: v1.1.0 - PmEmvReader Integration & Architecture Refactor
```

---

**Status:** 🟡 Ready for Beta Testing  
**GitHub:** https://github.com/chronlc/nf-sp00f33r-pro  
**License:** Check repository for details
