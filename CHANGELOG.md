# Release history

## 1.10.2 - 2026-11-13

- Bulk editing now includes an optional date with a calendar picker. Review the current/new dates before applying one date to all selected transactions, alone or alongside category/account changes.
- Date changes use the existing atomic batch save, stale-record protection and per-record edit history. Unchecked dates and already-correct rows are preserved.

## 1.10.1 - 2026-11-12

- Double-click a record in Income & expenses or an account ledger to open the existing editor. Sorted and filtered rows resolve to the clicked record; modified clicks retain bulk-selection behavior.

## 1.10.0 - 2026-11-12

- Close-time automatic backups now retain the newest five per ledger. Added independent 10-minute backups, enabled by default, keeping five and skipping unchanged ledger data after an initial baseline.
- Manual and pre-restore safety backups remain unlimited and are never counted or pruned, even when placed in an automatic backup folder. Existing legacy backups are left intact.
- Backups publish a new complete snapshot before rotating older owned copies. Separate profile/type folders and ownership metadata protect other ledgers and backup kinds.
- Timed work uses a dedicated connection and consistent snapshots; stored content fingerprints survive restarts and do not mark post-snapshot changes as already backed up. A successful manual snapshot can suppress redundant timed copies in the current session.
- Added independent backup toggles in Settings; restoration preserves both local choices and the destination. No backup-format or database-schema change.

## 1.9.1 - 2026-11-12

- Added a persistent temporary update cache, verified receipts and per-version locking. Reopening a downloaded release verifies and reuses the saved installer across restarts instead of downloading it again.
- Missing receipts refresh the small checksum file; corrupt or incomplete installers are replaced only after a new download verifies. Cancellation and failures do not expose partial installers.
- New-version startup removes cached installers/receipts for the installed version and older versions, including legacy download folders, with background retries for Windows file locks. Newer uninstalled updates and unrelated files are preserved.
- Update prompts identify saved downloads; installation still rechecks the installer hash immediately before launch.

## 1.9.0 - 2026-11-12

- Added Recently deleted under Settings and Income & expenses to restore transactions, transfers and mileage deleted after this update, including across restarts.
- Delete/restore operations are atomic. Original IDs/data and edit history are retained, transfers restore both account effects, and conflicts never overwrite existing rows. Restoring matching transactions requires duplicate approval.
- Recovery metadata records deletion/restoration actors and times. Referenced accounts are archived instead of removed; category renames are followed on restore.
- Backup format 3 includes recovery entries/history. Formats 1 and 2 remain restorable with an empty recovery list; new backups require version 1.9.0 or newer.

## 1.8.0 - 2026-11-12

- Added Bulk edit selected on Income & expenses: Ctrl-click or Shift-click multiple transactions, choose category/account changes and a required reason, then review every selected row before saving.
- Bulk changes are atomic and add an individual before/after edit-history entry for each changed transaction. Already-correct rows remain unchanged without extra history.
- Active target choices are revalidated, and stale or deleted selected transactions abort the batch. Transfers are excluded. Single-record edit/history/delete actions now require exactly one selected row.
- Successful changes refresh ledger balances, payee suggestions and the Dashboard; canceling keeps the selection and writes nothing.

## 1.7.0 - 2026-11-12

- Added manual-entry duplicate review with existing transaction details and Cancel / Add anyway choices, defaulting to Cancel.
- Unified possible-duplicate matching for manual entry and CSV import: same date, type, amount, account and normalized full payee, regardless of category or notes. Transfers are excluded.
- CSV previews show match sources. Review duplicates lists all flagged rows with per-row Keep choices; imports default to skipping other possible duplicates and confirm their planned counts.
- Import and manual saves recheck matches while serialized with transaction writes; CSV imports remain atomic.

## 1.6.2 - 2026-11-12

- Income & expenses focuses Payee / customer when opened and after a successful save. Tab accepts a payee completion without leaving the field; the next Tab moves to Amount. Without a completion, Tab moves directly to Amount.
- Ctrl+Enter applies the suggested payee rule without submitting the transaction or moving focus. Plain Enter adds only when payee and amount are filled in; new transactions require a payee.

## 1.6.1 - 2026-11-12

- Added extra width to form-field labels so Windows/Nimbus rendering does not clip Amount, Payee / customer, Notes, Search ledger, and other captions. Labels and their inputs continue to wrap together.

## 1.6.0 - 2026-11-12

- Added exact-match payee/category/account suggestions to the new transaction form, separated by Income and Expense. Matching ignores capitalization and repeated whitespace.
- Suggestions come from saved rules or the latest dated eligible transaction. Apply suggestion explicitly changes only the category/account; entering a payee never overwrites form choices.
- Save payee rule remembers the current selection; Manage payee rules lists and removes preferences. Existing transactions remain unchanged. Archived choices disable saved suggestions, and category renames preserve rules.
- Saved rules are isolated per ledger profile and included in version-2 backups. Version-1 backups remain restorable, with no saved rules; earlier application versions cannot read the new backup format.

## 1.5.0 - 2026-11-12

- Added account-specific ledgers from clickable Dashboard account names/balances and Accounts & transfers → View account ledger.
- Account ledgers show dated transactions, transfers, audit markers, and full-history running balances, with search, date/type/category/amount filters, edit access, and edit history.
- Period summaries include carried-forward balances and all account movement, independently of income-analysis settings and row search filters.
- Dashboard entry points retain the selected date range and use stable account IDs even when the balances table is sorted. Archived and empty accounts are supported.
- Added in-app help for account ledgers.

## 1.4.3 - 2026-11-11

- Newly created login accounts open Accounts & transfers so first-time users can set up accounts and opening balances.
- Returning sign-ins continue to open the Dashboard; in-app getting-started help explains the first-sign-in behavior.

## 1.4.2 - 2026-11-11

- Fixed the Java taskbar icon by assigning the Java host and Swing windows a stable Windows application identity with Ledger's icon and relaunch command.
- Desktop and Start menu shortcuts use the same application identity and stable launcher icon, preserving taskbar grouping across releases.
- Portable updates retain uniquely versioned native integration files so a running app's DLL is never replaced.

## 1.4.1 - 2026-11-11

- Added a custom navy and teal bookkeeping ledger icon with a gold bookmark and checkmark.
- Application and login windows use icons at seven resolutions; Windows application, setup, launcher, uninstall, and shortcut icons use the matching multi-resolution ICO.
- Build and publishing scripts include the icon assets, with binary-safe Maven resources.

## 1.4.0 - 2026-11-11

- Download updates inside Ledger with progress and cancellation, without opening a browser.
- Verify installer size and SHA-256 against the release checksum before offering Install and restart; verify again before launching.
- Install and restart honors automatic backup-on-close and keeps the app open if backup is canceled or installer launch fails.
- Installer update handoff waits for the original app and launcher bridge to exit, installs versioned files with the same ledger data folder, and reopens Ledger at login.
- Full install/update/restart lifecycle still requires a disposable Windows user/VM test; no real installation is run for build validation.

## 1.3.2 - 2026-11-11

- Dashboard shows a red exclamation indicator, available version, and View update button when a newer stable release is found.
- The reminder remains after choosing Later or opening a download and reopens the update prompt without another network check.
- Successful checks with no newer release hide the reminder; failed checks preserve an already detected update for the current session.

## 1.3.1 - 2026-11-11

- Business mileage now requires only a valid date and positive miles. From, To, and Business purpose are optional and can all be left blank.
- Updated mileage field labels and in-app help to explain the required and optional fields.

## 1.3.0 - 2026-11-11

- Publishing script supports Windows PowerShell 5.1 without newer .NET path APIs and handles missing GitHub repositories, remotes, and releases during setup.

- Public source and installer release hosting prepared for GitHub at Mr-Cee/Business-Ledger.
- Checks for newer stable GitHub releases in the background after login, with an installer download prompt and release notes.
- Help → Check for updates checks on demand; offline startup remains usable without an error prompt.
- Update checks require the expected Windows installer and checksum assets, reject unexpected download locations, and do not upload ledger data.

## 1.2.0 - 2026-11-11

- Added an offline, searchable Help guide with first-use instructions, screen and button explanations, date ranges, imports, settings, backups, and troubleshooting.
- Help for this screen and F1 open instructions for the selected main tab.
- Login and Settings offer Help buttons and F1 instructions, including guidance on separate ledgers and restoring backups.

## 1.1.0 — 2026-11-11

- All-in-one installer includes private PostgreSQL 17 binaries, Java, and app-local Microsoft C++ runtime dependencies.
- Fresh PCs no longer need a separate PostgreSQL installation or dependency download.
- Startup prefers the bundled database tools; development builds can still use an explicit external installation.
- Updates and uninstall gracefully stop only app-owned bundled database servers; external PostgreSQL installations and ledger data are retained.
- Bundled DPAPI credential helper removes PowerShell from installed credential startup while preserving existing encrypted credentials.
- Vendor license notices and runtime file inventories are included with the package.

## 1.0.1 — 2026-11-11

- Fixed startup of a new data folder while another ledger database is running: each data folder remembers its own available local port.
- Reconnecting uses the running cluster's actual port and verifies its data directory before opening ledgers.
- Serialized startup within a data folder to avoid concurrent initialization.
- Setup now shows application and data locations separately and explains how to start an empty ledger for backup restore.
- Restricted builds use a version-stamped launcher bridge with the existing Java runtime.

## 1.0.0 — 2026-11-11

First tracked release of Business Ledger.

- Separate login ledgers with income, expenses, accounts, transfers, and mileage.
- Editable records with reasons and audit history; CSV import and report templates.
- Dashboard, monthly charts, account balance history, payee pie chart, and transfer views.
- Ledger search, calendar filters, themes, income category preferences, and estimated taxes.
- Portable backups, transactional restore with safety copies, and optional backups on close.
- Help → About displays the release version.
- Per-user Windows installation and versioned application files for future updates.

The application includes its Java runtime. PostgreSQL 17 remains a prerequisite. Installers are unsigned. Ledger databases and backups live outside installed application files and are retained during updates and uninstall.
