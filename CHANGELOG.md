# Release history

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
