# Loaner

Loaner is a private, offline-first Android app for tracking gold loans from banks and pawn shops.

## Features

- Bank and pawn-shop lender management
- Loan holder autocomplete and lender-aware loan entry
- Principal, monthly/yearly interest, accrued interest, total, and elapsed-period calculations
- Bank due dates with 30-day orange and 7-day red warnings
- Loan filtering and sorting
- Closure amounts and renewal-cycle history
- Dashboard totals for active and closed loans
- Optional local 4-digit PIN with an in-app keypad
- Complete JSON backup/import (the PIN is deliberately excluded)
- GitHub Releases update checking

Interest is estimated as annual simple interest using actual elapsed days divided by 365. Bank due dates are one year after the loan date, minus one day.

## Build

Requirements: Android SDK 36 and JDK 17 or newer supported by your Gradle version.

```powershell
./gradlew test assembleDebug
```

For a signed release, create `keystore.properties` at the project root (it is ignored by Git):

```properties
storeFile=loaner-release.jks
storePassword=your-password
keyAlias=loaner
keyPassword=your-password
```

Then run `./gradlew test assembleRelease`. Keep the keystore safe: Android updates must be signed with the same key.

## Privacy

All loan data remains in the app's private on-device storage unless you explicitly export a backup. The optional PIN hash is stored separately and is never included in backups.

