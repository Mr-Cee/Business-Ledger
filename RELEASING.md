# Building and installing releases

The release version lives in `pom.xml` as `major.minor.patch`. Start with **1.0.0**; increment patch for compatible fixes, minor for compatible features, and major for incompatible changes. Record every release in `CHANGELOG.md`. Build scripts and Maven embed the version in `release.properties`, and the application shows it in **Help > About** and its title bar.

## Build an installer

Run `./Build-Installer.ps1` from PowerShell on the build PC. It uses the installed JDK/jpackage, PostgreSQL JDBC JAR, and Windows .NET Framework compiler. The output is `releases/BusinessLedger-Setup-<version>.exe`, its SHA-256 file, and the changelog. It bundles private Java and PostgreSQL 17 runtimes, a Windows DPAPI credential helper, and app-local Microsoft C++ runtime DLLs. Recipients on Windows 10/11 x64 need no separate Java, PostgreSQL, or runtime downloads. The build PC needs PostgreSQL 17 in the standard location (or the Bundle-Database.ps1 source override), including its original vendor redistributable and license notices. Only bin/lib/share are copied; never data. The installer targets 64-bit Windows and is not code-signed; Windows may identify the publisher as unknown.

The build refuses to replace an existing release artifact. Use `-Rebuild` only for unreleased development builds; increment the version after distribution.

The setup installs for the current Windows user under `%LOCALAPPDATA%/Programs/BusinessLedger`. It creates desktop and Start menu shortcuts and an Apps & features uninstall entry. No administrator installation is required. Future versions use the same setup workflow and product registration. Newer installed versions block older installers. Close the running application before installing.

In a restricted build environment where native jpackage resource editing is blocked, `./Build-Installer.ps1 -UseExistingRuntime` packages the working launcher/runtime from `dist/BusinessLedger` with freshly compiled application files. Starting in 1.0.1, a version-stamped Windows bridge starts the native Java host; both are managed application files, and setup checks that both are closed before updating. Packaged launch verification is required after building with this mode.

## Keep existing data

The setup shows a **Ledger data folder** before installation. On this PC it detects the existing project folder containing `.local/postgres-data`; leave that selection unchanged to retain the complete login catalog and all ledgers. On later updates it remembers the selected folder. Fresh installations default to `%LOCALAPPDATA%/BusinessLedger/Data`. The location is stored in `%LOCALAPPDATA%/BusinessLedger/data-location.txt`.

Setup shows **Application location** separately. That is where the executable lives; the editable **Ledger data location** is for databases and backups. To start fresh for backup restore, choose a new data location, create a login after startup, then restore. From 1.0.1 onward, each data location uses a saved available loopback port, allowing the original and new clusters to coexist. Startup verifies PostgreSQL's `data_directory` before creating or opening application databases.

Setup changes application files and this location pointer; it does not copy, replace, or reset ledger databases. Application versions are kept in separate immutable directories, and shortcuts point through a stable launcher. Uninstall removes managed application files and registration, retains data-location configuration, and preserves ledger data. Files added manually to the installation directory are not recursively removed. Existing backups stay in their chosen locations.

To move to a different PC, install Business Ledger there, create a login, and restore a `.ledgerbackup` into its ledger. App backups are per ledger; they do not contain the old PC's login catalog or DPAPI-protected database credential.

## Development / portable updates

Use `./Update-Executable.ps1` to update the existing `dist/BusinessLedger` application. It creates a new uniquely named JAR and atomically changes the launch configuration. It retains the JAR used by a running instance; the next launch loads the update. Do not copy over `dist/BusinessLedger/app/business-ledger.jar` in place.

Use `./Build-Executable.ps1` for a new portable application image in a unique release directory. It keeps this project's existing ledger home. `-Layout Installed` builds an image with no hardcoded data location, for setup packaging. Neither operation overwrites an existing application image. Maven-built JARs can use `-Dledger.home="C:/path/to/existing/ledger-home"` to select an existing data folder explicitly.

## Verification and distribution

### GitHub hosting and update notices

Public source and downloads use `Mr-Cee/Business-Ledger`. Install GitHub CLI on the publishing PC and run `gh auth login` once; never put tokens in source or the application. Configure your Git author name/email. Increment `pom.xml`, update `CHANGELOG.md`, build with `Build-Installer.ps1`, and verify the package and native launcher. Then run `./Publish-Release.ps1`.

The publishing script stages only explicitly allowed source/docs (not databases, credentials, backups, personal CSVs, IDE files, binaries, or build output), pushes source, creates a draft release, uploads the installer and checksum, and publishes only after both uploads succeed. It refuses to overwrite an existing release. If an upload fails, complete the existing draft on GitHub or delete the unpublished draft before retrying; do not replace published assets. Installers belong in Release assets, not Git history. Runtime distributions remain bundled in the installer with their vendor notices.

The application checks `/repos/Mr-Cee/Business-Ledger/releases/latest` after login and from Help. It compares numeric major/minor/patch versions, skips drafts/prereleases, and requires the expected installer and checksum assets from the same repository. Checks run off the UI thread and send no ledger content. API rate limits or offline startup do not prevent use.

From 1.4.0, downloads run inside the app with cancellation, size limits, HTTPS redirect restrictions to GitHub asset hosts, and SHA-256 verification against the release checksum. The installer is verified again before execution. Install and restart completes the existing backup-on-close flow before starting the downloaded installer with separate arguments: `/update <parent PID> <absolute ledger home>`. Only then does the app exit. If backup is canceled or process launch fails, the app stays open. The installer waits for the exact parent process to exit and gives the launcher bridge time to close; it never kills an app process. It reuses the standard versioned installation and downgrade guards, preserves the supplied data home, shows installation progress, and reopens the stable launcher after success. Sign-in is required again. Portable copies use the normal per-user installation location when installing an update; they retain their ledger home. Older apps use their prior browser/manual flow once to install 1.4.0.

Downloader integrity, cancellation, redirect trust, tampering, and command-argument checks plus installer handoff parsing/timeout checks can run without installing anything. `/verify` remains payload-only. Full download/close/install/reopen and failure-recovery validation must run in a disposable Windows user/VM, never by installing over the developer's live ledger for testing.

The installer supports `/verify <absolute report path>` for build verification: it checks its embedded payload hash, extracts it into a sibling test directory, checks path containment, confirms the Java runtime and version handling, and confirms the installed image has no hardcoded data path. It does not install, change shortcuts, edit the registry, or touch ledger databases in this mode.

Before distributing a release, run the Java release/data-location checks and relevant feature checks. Verify setup in a disposable Windows user/VM: clean install, update, downgrade refusal, shortcut launch, preserving the selected ledger home, and uninstall with data retention. Publishing to other users should use a trusted distribution location and a code-signing certificate. Keep versioned release artifacts and their hashes; never silently change a previously distributed version.

## All-in-one validation

The build audits native imports against packaged C++ runtime files, validates vendor runtime exports, and runs the bundled PostgreSQL version command. The credential helper uses Windows DPAPI with CurrentUser scope, compatible with existing saved credentials; no credential is passed on a command line. Development-only fallback may still use PowerShell.

Release checks exercise a fresh cluster with system PostgreSQL lookup disabled, using bundled Java and PostgreSQL: account creation, transactions, backup/restore, graceful owned-server shutdown, and restart. A packaged native launcher check opens no ledger. These checks do not substitute for clean Windows VM install/update/uninstall testing.
