# Phase 1 Complete Replacement Plan

## ALL Hard-Coded Values Found in CardReadingScreen.kt

### COLORS TO REPLACE (with line numbers from file read):
Line 113: `Color(0xFF1B4332)` → `CardReadingColors.SafeBackground`
Line 113: `Color(0xFF4A1A1A)` → `CardReadingColors.VulnerableBackground`
Line 114: `16.dp` → `CardReadingRadius.ExtraLarge`
Line 117: `12.dp, 6.dp` → `CardReadingSpacing.Medium, CardReadingSpacing.Small / 2`
Line 124: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 124: `Color(0xFFFF6B6B)` → `CardReadingColors.BrightRed`
Line 125: `14.dp` → `14.dp` (keep - icon size)
Line 130: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 130: `Color(0xFFFF6B6B)` → `CardReadingColors.BrightRed`

Line 217: `36.dp` → `CardReadingDimensions.ButtonHeightSmall`
Line 219: `Color(0xFF1A1F2E)` → `CardReadingColors.ButtonBackground`
Line 220: `Color(0xFFFFFFFF)` → `CardReadingColors.TextPrimary`
Line 222: `1.dp, Color(0xFF333333)` → `1.dp, CardReadingColors.BorderDark`
Line 223: `12.dp, 0.dp` → `CardReadingSpacing.Medium, 0.dp`
Line 235: `Color(0xFF1A1F2E)` → `CardReadingColors.ButtonBackground`
Line 242: `Color(0xFFFFFFFF)` → `CardReadingColors.TextPrimary`

Line 259: `6.dp` → `CardReadingSpacing.Small / 2`
Line 266: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`
Line 273: `36.dp` → `CardReadingDimensions.ButtonHeightSmall`
Line 275-276: `Color(0xFF1A1F2E)`, `Color(0xFFFFFFFF)` → ButtonBackground, TextPrimary
Line 278: `1.dp, Color(0xFF333333)` → `1.dp, CardReadingColors.BorderDark`
Line 279: `12.dp, 0.dp` → `CardReadingSpacing.Medium, 0.dp`
Line 295: `Color(0xFF1A1F2E)` → `CardReadingColors.ButtonBackground`
Line 302: `Color(0xFFFFFFFF)` → `CardReadingColors.TextPrimary`

Line 322-324: Scan button colors → CardReadingColors.ErrorRed, SuccessGreen, TextTertiary
Line 343: `40.dp` → `CardReadingDimensions.ButtonHeightMedium`
Line 347: `6.dp` → `CardReadingRadius.Medium`
Line 348: `16.dp, 0.dp` → `CardReadingSpacing.Large, 0.dp`
Line 352: `8.dp` → `CardReadingSpacing.Small`
Line 360: `Color(0xFF0A0A0A)` → `CardReadingColors.Background`
Line 361: `18.dp` → `18.dp` (keep - icon size)
Line 365: `Color(0xFF0A0A0A)` → `CardReadingColors.Background`
Line 377: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`

RocaVulnerabilityStatusCard (lines 388-441):
Line 393: `Color(0xFF4A1A1A)` / `Color(0xFF1B4332)` → VulnerableBackground / SafeBackground
Line 396: `8.dp` → `CardReadingRadius.Large`
Line 404-405: `16.dp`, `12.dp` → `CardReadingSpacing.Large`, `CardReadingSpacing.Medium`
Line 412: `Color(0xFFFF6B6B)` / `Color(0xFF00FF41)` → BrightRed / SuccessGreen
Line 420: `32.dp` → `CardReadingSpacing.Huge`
Line 429: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`
Line 436: `Color(0xFFFF6B6B)` / `Color(0xFF00FF41)` → BrightRed / SuccessGreen

ActiveCardsSection (lines 445-481):
Line 452: `12.dp` → `CardReadingSpacing.Medium`
Line 458: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 463: `16.dp`, `4.dp` → `CardReadingSpacing.Large`, `CardReadingSpacing.Tiny`
Line 473: `8.dp` → `CardReadingSpacing.Small`
Line 476: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`

ApduTerminalSection (lines 485-557):
Line 490: `Color(0xFF0F1419)` → `CardReadingColors.CardBackground`
Line 491: `8.dp` → `CardReadingRadius.Large`
Line 493-494: `16.dp`, `12.dp` → `CardReadingSpacing.Large`, `CardReadingSpacing.Medium`
Line 506: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 511: `8.dp` → `CardReadingSpacing.Small`
Line 514: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`
Line 524: `300.dp` → `CardReadingDimensions.TerminalHeight`
Line 526: `Color(0xFF000000)` → `CardReadingColors.TerminalBackground`
Line 527: `8.dp` → `CardReadingRadius.Large`
Line 529: `12.dp` → `CardReadingSpacing.Medium`
Line 538: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 548: `6.dp` → `CardReadingSpacing.Small / 2`

ApduLogItemParsed (lines 561+):
Line 567: `2.dp` → `2.dp` (keep - fine detail spacing)
Line 571: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 577: `35.dp` → `35.dp` (keep - specific terminal layout)
Line 581: `Color(0xFF00FF41)` → `CardReadingColors.SuccessGreen`
Line 585: `11.sp` → `11.sp` (keep - font size, not spacing)
Line 595: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`
Line 597: `10.sp` → `10.sp` (keep - font size)
Line 605: `Color(0xFF2196F3)` → `CardReadingColors.InfoBlue`
Line 611: `35.dp` → `35.dp` (keep)
Line 614: `6.dp` → `CardReadingSpacing.Small / 2`
Line 621: `Color(0xFF2196F3)` → `CardReadingColors.InfoBlue`
Line 627: `11.sp` → `11.sp` (keep)
Line 639: `4.dp`, `4.dp, 2.dp` → `CardReadingRadius.Small`, `CardReadingSpacing.Tiny, 2.dp`
Line 644: `10.sp` → `10.sp` (keep)
Line 651: `8.dp` → `CardReadingSpacing.Small`
Line 653: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`
Line 655: `9.sp` → `9.sp` (keep)
Line 659: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`
Line 661: `9.sp` → `9.sp` (keep)
Line 674: `35.dp, 2.dp` → `35.dp, 2.dp` (keep)
Line 678: `Color(0xFFFFB74D)` → `CardReadingColors.WarningOrange`
Line 683: `9.sp` → `9.sp` (keep)
Line 689: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`
Line 691: `9.sp` → `9.sp` (keep)
Line 697: `6.dp` → `CardReadingSpacing.Small / 2`

decodeStatusWord function (lines 704+):
Line 706: `Color(0xFF4CAF50)` → `CardReadingColors.BrightGreen`
Line 707: `Color(0xFF2196F3)` → `CardReadingColors.InfoBlue`
Line 708-709: `Color(0xFFFF9800)` → `CardReadingColors.Orange`
Line 710: `Color(0xFFFF9800)` → `CardReadingColors.Orange`
Line 711: `Color(0xFFFF9800)` → `CardReadingColors.Orange`
Line 712-730: `Color(0xFFF44336)` → `CardReadingColors.DangerRed`
Line 732: `Color(0xFF2196F3)` → `CardReadingColors.InfoBlue`
Line 734: `Color(0xFFFFFF9800)` → `CardReadingColors.Orange`
Line 736: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`

EmvDataDisplaySection (lines 758+):
Line 763: `Color(0xFF1A1A1A)` → `Color(0xFF1A1A1A)` (darker variant, keep or add new color)
Line 766: `8.dp`, `16.dp` → `CardReadingRadius.Large`, `CardReadingSpacing.Large`
Line 776: `Color(0xFF00FF00)` → `CardReadingColors.SuccessGreen` (or Bright variant)
Line 781: `Color(0xFF666666)` → `CardReadingColors.TextTertiary`
Line 785: `12.dp`, `300.dp`, `4.dp` → `CardReadingSpacing.Medium`, `CardReadingDimensions.EmvDataMaxHeight`, `CardReadingSpacing.Tiny`
Line 796: `Color(0xFF00FFFF)` → `CardReadingColors.Cyan`
Line 798: `4.dp` → `CardReadingSpacing.Tiny`
Line 814: `Color(0xFF00FFFF)` → `CardReadingColors.Cyan`
Line 816: `4.dp` → `CardReadingSpacing.Tiny`
Line 832: `Color(0xFF00FFFF)` → `CardReadingColors.Cyan`
Line 834: `4.dp` → `CardReadingSpacing.Tiny`
Line 851: `Color(0xFFFF5722)` → `Color(0xFFFF5722)` (deep orange, could add)
Line 853: `8.dp` → `CardReadingSpacing.Small`
Line 859: `4.dp` → `CardReadingSpacing.Tiny`
Line 876: `Color(0xFF00FFFF)` → `CardReadingColors.Cyan`
Line 878: `4.dp` → `CardReadingSpacing.Tiny`

EmvFieldRow (lines 894+):
Line 904: `2.dp` → `2.dp` (keep)
Line 907: `Color(0xFF888888)` → `CardReadingColors.TextSecondary`
Line 912: `Color(0xFF00FF00)` → `CardReadingColors.SuccessGreen` (or BrightGreen)

## VERIFIED REPLACEMENTS NEEDED: 60+ color instances, 30+ spacing values
