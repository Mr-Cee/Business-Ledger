# Business Ledger

**Current version: 1.8.0.** [Download published installers](https://github.com/Mr-Cee/Business-Ledger/releases/latest). Java, PostgreSQL 17, and the Microsoft C++ runtime dependencies are included for Windows 10/11 x64. No additional runtime downloads are needed. The installer is unsigned. For a manual installation, close Ledger and keep your existing ledger data folder selected. See [RELEASING.md](RELEASING.md) for builds, installation, and verification limits.

## Bulk editing

In Income & expenses, use Ctrl-click for individual rows or Shift-click for a range, then choose Bulk edit selected. Filtering and sorting are supported: only the selected visible transactions are included. Select Income/Expense rows only; transfers must be managed in Accounts & transfers.

Check Change category, Change account, or both; choose active targets and enter a reason. Unchecked fields keep each transaction's original value, including archived or unassigned accounts. Review the before/after table and choose Save changes. Date, type, amount, payee and notes are preserved. Rows that already have the chosen values are listed as Unchanged and receive no extra edit history.

The full batch saves together with an individual audit entry per changed record. If any selected transaction changed or disappeared since loading, a target was archived, or any update/history write fails, no bulk changes are saved. Reopen the editor and review again. Canceling keeps your selection. Single-record edit, history and delete actions require exactly one selected row.

## Duplicate detection

Manual entries warn when date, type, amount, account and full payee name match a saved transaction. Payee matching ignores capitalization and extra whitespace; category and notes may differ. Review the existing rows and choose Cancel or Add anyway for a legitimate separate transaction. Transfers are not compared.

CSV previews flag matches against the current ledger and earlier rows in the file. Skip possible duplicates is on by default. Review duplicates lists all flagged rows, including those past the first 200 preview rows; check Keep for individual legitimate repeats. Returning to mapping and validating again resets Keep choices. Turning skipping off imports all rows after confirmation. Blank CSV payees match other blank payees with the same date/type/amount/account, so review those carefully. Imports recheck matches before saving; final counts can differ if the ledger changed after preview. Importing is atomic; canceling review or manual confirmation writes nothing.

## Keyboard transaction entry

Opening Income & expenses focuses Payee / customer. Tab accepts its autocomplete without leaving the field; the next Tab moves to Amount. With no completion, Tab moves directly to Amount. Ctrl+Enter only applies the suggested rule and keeps focus where it is. Plain Enter submits only when payee and amount are filled in; all other transaction validation still applies. After saving, focus returns to Payee / customer.

## Payee rules and suggestions

In **Income & expenses**, enter a familiar payee/customer and choose Income or Expense. A suggestion appears for the full name (case and extra spaces are ignored). **Apply suggestion** sets the category and account; review them before adding the transaction. Date, amount, notes, and type stay as entered. Suggestions do not apply to imports, transfers, or edits of existing records.

Choose a category/account and click **Save payee rule** to remember that preference. Saving the same payee and type replaces its rule. **Manage payee rules** lists saved rules and lets you remove one; to change a rule, save a replacement from the transaction form. Rules belong to the current login's ledger. Without a saved rule, suggestions use the latest dated transaction with an active category and account, breaking same-date ties by transaction ID. Partial names never match.

Archived categories/accounts make their saved rules unavailable until restored or replaced. Renaming a category preserves saved rules. Removing a rule allows transaction-history suggestions again. Rules are included in new version-2 backups; old version-1 backups restore with no saved rules. Version-2 backups require Business Ledger 1.6.0 or newer.

## Account ledgers

Click an account name or balance on the Dashboard to open its ledger for the Dashboard's date range. You can also select a Dashboard account and click **View selected ledger** or press Enter. In **Accounts & transfers**, select an account and choose **View account ledger**, or click its recorded balance, to view all dates. Archived accounts are supported.

The ledger shows opening/carried-forward balance, period money in/out, closing balance, transactions, transfer directions/counterparties, running balances, and Edited markers. Transfers and all income categories count toward actual account balances, regardless of income-analysis settings. Search/type/category/amount filters only narrow rows; dates control the period summary. Clear filters restores all dates for the same account. Select a record to edit it with a reason or view its audit history; transfers use the existing paired-transfer editor. Balances are calculated before row filtering or sorting. In narrower windows the financial columns appear first, with sideways scrolling for details. F1 opens account-ledger help.

After creating a login, the first session opens **Accounts & transfers** to help set up accounts and opening balances. Returning sign-ins open the Dashboard.

Windows taskbar windows and shortcuts share the Ledger application identity and bookkeeping icon. If an older Java icon was pinned before 1.4.2, unpin that old item once and pin the updated Ledger window or its installed shortcut instead.

Business mileage requires a valid date and positive miles. From, To, and Business purpose are optional.

## Updates

When an update is found, the Dashboard shows a red exclamation mark, its version, and a **View update** button. The reminder stays visible after choosing Later and lets you reopen the download prompt without waiting for another check. It reflects updates detected in the current app session; a successful check with no newer version hides it.

Version 1.3.0 and later check the public GitHub release feed after login. **Help > Check for updates** checks on demand. Starting in 1.4.0, **Download update** downloads inside Ledger with progress and cancellation. The app checks size and SHA-256 against the published checksum. Choose **Install and restart** to complete any configured close-time backup, close the app, install into a new version folder, and reopen at login. The same ledger data folder is kept. Choosing Later retains the verified download for the current session. A failed download or canceled backup leaves Ledger open. Release notes still open in the browser. Network failures do not prevent normal use, and no financial data or login credentials are sent. Versions before 1.4.0 require one update using their existing browser/manual-install flow to gain in-app installation.

Source commits alone do not trigger update notices. Publish a GitHub Release with a version tag (for example `v1.3.0`), `BusinessLedger-Setup-1.3.0.exe`, and `BusinessLedger-Setup-1.3.0.sha256`. Drafts, prereleases, incomplete uploads, and older versions are ignored. Use `Publish-Release.ps1` after building and verifying a release.

The installed executable is `%LOCALAPPDATA%/Programs/BusinessLedger/BusinessLedgerLauncher.exe`, available through desktop and Start menu shortcuts. The selected data folder (for example `C:/BusinessLedger`) contains databases and backups, not the executable. Each data folder remembers its PostgreSQL port in `.local/postgres-port.txt`; a new folder chooses another free loopback port if the original port is occupied. For manual database tools, use the saved port for the selected data folder rather than assuming 55432.

A Java desktop starting point for a single business, using PostgreSQL to persist records. The interface uses Java Swing. Amounts are in USD and distances in miles.

## Login accounts and separate ledgers

Choose Create account on the login window. The first login account inherits the existing `business_ledger` database. Every later account gets a separate database with its own financial accounts, transactions, transfers, mileage, categories, report templates, and remembered transaction date. Names are case-insensitive and passwords are case-sensitive. Passwords require 12–256 characters and are stored as PBKDF2-HMAC-SHA256 hashes with 600,000 iterations and a unique 32-byte random salt. Login passwords are never saved in plaintext or as reversible encryption.

The login window lists saved account names and remembers the last successful login. Type a name or select it from the dropdown. The password field receives focus when an account exists, and Enter logs in. Creating an account also logs you in. The title bar shows the login name. Close and reopen to switch profiles. Password reset and login-account deletion are not yet included.

Existing installations ask once for the old database password before showing the login screen. Windows DPAPI protects that database credential for the current Windows user, so later launches need only the ledger login. Fresh installations generate this credential automatically. The encrypted credential is `.local/database-credential.dpapi`; the login catalog is the database `business_ledger_profiles`. Back up the catalog and every profile database along with the original ledger. DPAPI credentials are tied to this Windows user; copying the project to another Windows account or PC requires database credential setup. App logins separate ledgers within the application; they do not encrypt database contents at rest or protect against someone who controls the Windows account or PostgreSQL administrator.

## Backup and restore

Open **Settings > Open settings** (Ctrl+,) and scroll to **Backup & restore**. **Back up ledger** saves a dated `.ledgerbackup` file with the signed-in ledger's transactions, accounts, transfers, mileage, categories, report templates, edit history, and ledger preferences. It uses a consistent database snapshot and writes the completed file atomically where supported. Login identities/passwords and this PC's theme are separate; a backup is for one ledger, not all PC logins. Backups contain unencrypted financial data.

**Restore ledger** replaces the signed-in ledger after confirmation and automatically saves a dated safety backup first. The archive uses a fixed data format and checksums, with no executable SQL. Invalid data rolls back the restore, including identity counters. Other ledgers are unaffected. On another PC, install/start Business Ledger, create a login, then restore the backup into that login's ledger. This PC's backup folder and automatic-backup choice are retained when restoring.

To enable automatic backups on normal app close, check **Back up this ledger when closing the app**, choose a **Backup folder**, and click **Save settings**. It is off by default and saved per ledger. Automatic and safety backups default to `backups/<ledger database>/` under the project folder. Every backup gets a dated, unique filename; older backups are retained. If an automatic backup fails, the app offers Retry, Close without backup, or Cancel. Forced termination and power loss cannot trigger this close-time backup.

## Features

On **Income & expenses**, use **Search ledger** (Ctrl+F) to find records as you type. Matching is case-insensitive and treats punctuation literally; every search word can match across ID, date, type, category, amount, payee/customer, notes, account, or edited marker. **Type** can include income, expenses, both transfer directions, or a single transfer direction. **More filters** adds category, account, inclusive From/Through dates with calendar buttons, and minimum/maximum transaction amounts. Blank dates/amounts mean no limit. Filters combine, **Clear filters** restores the full ledger, and the matching record count is displayed. Extra filters remain active when collapsed and are marked **More filters (active)**. Invalid filters show an inline message and no results until corrected. Filters survive record refreshes within the session; running balances always come from the full ledger and edit/delete actions use the original record IDs.

Choose **Charts > Account transfers** for transfer visualization using the same date presets as other charts. **By account** shows paired incoming/outgoing transfer bars, net movement, and transfer counts for every account, including archived accounts. **Between accounts** ranks directed routes (for example Checking to Savings); reverse-direction transfers are separate. Hover a row for full names, exact amounts, and counts, or use the sortable table. The total transferred counts each transfer once; account incoming and outgoing totals each reconcile to that total. Opening balances and income/expense transactions are excluded from these views. Transfers remain excluded from income/expense reports and charts.

Choose **Top payees (pie)** on the **Charts** tab to compare expenses by payee for the selected date range. The eight highest-spend payees get individual slices; the rest are combined into **Other payees** so the pie represents all expenses. Blank names appear as **Unspecified payee**. Hover over a slice or legend row to see the full payee name, exact total, percentage of expenses, and expense count. The table shows exact totals and percentages. Income and transfers are excluded. The legend moves below the pie in narrower windows, and light/dark themes are supported.

The Dashboard includes an **Estimated taxes due** card for the current calendar year. It multiplies recorded income for that year by **24%** by default, follows the selected income category preference, and stays on the current year regardless of the dashboard date range. Change **Estimated taxes > Percentage of income** in **Settings > Open settings**, then click **Save settings**. Rates from 0% to 100% (up to two decimal places) are saved per ledger across sessions. This is a percentage estimate of income; expenses and tax payments are not deducted. Hover over the amount for the income and rate used.

- Dated income and expenses with categories, payee/customer, and notes.
- Selecting Income defaults the category to Paycheck; you can then choose another category.
- Manage categories: add, rename, or remove choices from the dropdown. Renaming updates existing transactions. Removing a category preserves its historical transactions and report totals; adding the same name restores it. Paycheck is reserved as the Income default.
- Mileage records with date, origin, destination, business purpose, and distance.
- Inclusive date-range reports: category totals, income, expenses, net income, and business miles.
- CSV summary export and confirmed deletion of records.

## Accounts and transfers

Use Accounts & transfers to add Checking, Savings, Credit, Cash, or Other accounts with your own names. Checking, Savings, and Credit are created once as starting choices. Edit an account to change its name, type, or opening balance. Opening balances represent the balance before all recorded entries; credit debt must be entered as a negative amount. Recorded balances include all entries, including future dates.

Select an account when adding income or expenses. Earlier transactions remain Unassigned; select a transaction and use Assign / change account to include it in the appropriate account balance. Unassigned records still appear in reports covering all accounts.

Income & expenses shows a Running balance column immediately after Account. It is that account's opening balance plus its recorded movements through the transaction's date and position. Entries are calculated oldest first, even though the table initially displays newest first. On a shared date, transfers apply before transactions, and transactions apply in ID order. Sorting the table does not change these historical balances. Unassigned transactions show a dash. Changes to opening balances, account assignments, transactions, or transfers recalculate the column.

Enable Color rows above the main ledger to shade entire rows: light red for Expense/Transfer Out and light green for Income/Transfer In. Selected rows retain the standard selection highlight for readability. The toggle applies immediately and is remembered separately in each login's ledger. Turning it off restores the normal background; sorting does not change which records receive each color.

Transfer between accounts records one amount moving out of the source and into the destination. Create transfers on Accounts & transfers. Each transfer also appears on the main Income & expenses ledger as Transfer Out on the source account and Transfer In on the destination account, with the other account shown under Payee / customer. Both rows show their account's running balance. They are views of the same transfer, not additional saved transactions. Manage or delete the transfer through Transfer history; the main ledger's transaction delete and account-assignment actions do not apply to transfer rows.

Credit card payments should be transfers from Checking to Credit, while credit card purchases should be expenses on Credit. Transfers remain excluded from income/expense reports (including the All Transactions report). Deleting a transfer removes both main-ledger rows and reverses both balance effects. Negative balances are allowed; the app does not enforce bank overdraft or credit limits.

Select a transfer in Transfer history and use Edit selected transfer to change its date (with calendar), amount, source/destination accounts, or notes. A reason is required. Edited appears in Transfer history and on both corresponding main-ledger rows. View transfer edit history shows the editor, timestamp, reason, and before/after values. The change and its history save atomically, balances recalculate, and Cancel leaves the record untouched. Transfers retain their IDs and are not duplicated. The original archived accounts may be retained; new account choices must be active. Unchanged edits do not create history entries. Transfer history snapshots are retained after deletion, as with transaction edit history.

Delete / archive account permanently deletes an unused zero-opening-balance account. An account referenced by transactions, transfers, or report templates, or with a nonzero opening balance, is archived instead. Archiving preserves history and balances and removes the account from new-entry choices. Use Restore account to make it available again.

Report templates can filter to a specific account, including archived accounts, or all accounts. Detailed reports and CSV exports include the account name. These accounts track your financial records and do not connect to your bank.

## CSV imports

Click Import CSV on Income & expenses. Choose a file, its encoding (UTF-8 or Windows-1252), delimiter (comma, semicolon, or tab), and whether the first row contains headers. On Column mapping, select source columns for Date, Amount (or Debit/Credit), and optional Type, Category, Payee/customer, Notes, and Account name. Columns not mapped to a ledger field are ignored. The CSV sample tab shows the first 25 rows.

Choose the date format explicitly and the amount convention: signed amounts with positives as income or expenses, all income, all expenses, or separate debit/credit columns. An optional mapped Type accepts Income/Expense, Credit/Debit, Deposit/Withdrawal, or In/Out and overrides signed-amount classification. Separate debit/credit rows must have exactly one positive side. Dollar values use a decimal point with up to two decimals; dollar signs, properly grouped thousands commas, and negative parentheses are supported.

Choose default category and account values for missing/unmapped columns. Mapped account values must match existing active account names; unknown accounts must be added first. Missing categories can optionally be created during import. Names match existing categories/accounts without regard to case where unambiguous. Transfers should be removed from the import CSV and recorded under Accounts & transfers; they are not imported as linked transfer records.

Click Validate & preview to review up to 200 converted rows, row-level errors, proposed new categories, and exact-duplicate counts. Any invalid record blocks the entire import until corrected. Exact duplicates compare date, type, category, amount, payee, notes, and account, including duplicates within the file and records already in the ledger. Skipping is on by default but can be disabled for legitimate identical transactions. Click Import transactions only after reviewing the results. The batch and new categories save atomically; cancellation or a failed batch leaves no partial import. The file itself is unchanged. Limits are 20 MB and 50,000 data rows per file. Imported transactions support the existing editor, balances, reports, and autocomplete.

## Charts

Window controls wrap to additional rows as width decreases, including chart selectors, report controls, transaction fields, and action buttons. Charts fit the available viewport width and scroll vertically for longer datasets; labels and values remain visible at narrower sizes. Navigation tabs scroll if they cannot all fit.

The Charts tab beside Reports provides Income vs expenses (including net), Expenses by category, and Monthly income & expenses. Select inclusive dates with the calendar controls and click Refresh charts. Switching back to Charts also refreshes data. Exact dollar totals appear in a sortable table below each chart. Empty months appear as zero, and negative net values extend left of the zero line. These charts use only the current profile's income and expense transactions, excluding transfers and opening balances. Their date controls are independent of report filters; chart ranges can span up to 100 years.

Monthly income & expenses uses paired green income and red expense columns under a single month/year label, plus a compact net amount. Months wrap into bands at narrow widths and share the same dollar scale. Hover a month for exact figures. The table shows one row per month with Income, Expenses, and Net columns rather than separate income/expense rows. Transfers remain excluded.

Choose Account balances over time for a line per account, with a matching legend and exact-value table. Use Balance interval to select Daily, Monthly, or Yearly. Points show closing balances on each day or period end; the final partial month/year closes on the selected Through date. Balances include opening balances, all earlier activity carried forward, and both transfer sides. Days with no activity carry the previous balance. Negative credit balances and archived accounts remain visible; Unassigned transactions cannot be included in an account's balance. Hover a line for its date, account, and exact value. This balance view intentionally includes transfers while the income/expense views continue to exclude them.

Use the Account dropdown beside Balance interval to show one account or All accounts. The chart lines, legend, closing summary, and exact-value table update together. Your selection remains selected when refreshing dates or changing the interval during the session.

All chart views share a Date range dropdown. Choose this/last month, quarter, or year; Specific month (month + year); Specific quarter (Q1–Q4 + year); Specific year; All recorded dates; or Custom dates. Years can be chosen from the dropdown or typed directly. Calendar presets cover full inclusive periods, including future-dated entries if present. All recorded dates spans saved transaction and transfer dates, or year-to-date if there are none. Presets refresh the chart automatically and remain selected when switching charts. Editing From/Through manually, including through a calendar, switches back to Custom dates. These period ranges are independent of the daily/monthly/yearly balance sampling interval.

Q1 (January–March), Q2 (April–June), Q3 (July–September), and Q4 (October–December) are also available directly in Date range. Choose a quarter, then the Year dropdown to select its year.

## Report templates

Reports has the same Date range controls as Charts, including direct Q1–Q4 choices with a year selector, specific months/quarters/years, current/previous periods, All recorded dates, and Custom dates. Select a range, then Run report or Export report CSV; both use the displayed inclusive dates with the selected template and amount filter. Manual/calendar changes to From or Through switch to Custom dates. Chart and report range selections are independent.

The Reports tab includes All Expenses, All Income, All Transactions, and All Food. All Food matches the Food category for either transaction type. Each report uses the inclusive From/Through dates you enter when running it.

All In/Out shows a single row per account with Total In and Total Out for the selected dates. Only income counts as In and expenses as Out; transfers and opening balances are excluded from this report, all other income/expense reports, their CSV exports, and income/expense charts. Accounts without matching transactions show zero, and archived accounts remain included. An Unassigned row appears if matching transactions have no account. CSV exports use the same three columns. This default is added once, and you can edit or delete it like other templates. The Account In/Out totals layout is also available for custom templates. Transfers still appear on the main ledger and affect account balances.

Use New template to name a report, select all transactions or a specific type, choose all categories or one category, and select Transaction details or Category totals. Save with OK. Edit template changes the selected report; Delete template removes it after confirmation. All templates, including the defaults, can be edited or deleted. Deleted defaults do not return on restart. Templates are stored in your ledger database; date ranges are chosen separately each run.

Templates support multiple categories: uncheck All categories and check each category to include. A transaction matches any checked category, together with the template's type/account and current date/amount filters. Choose All categories to include every category, including future ones. Existing single-category templates retain their selections. Edit template restores all saved checks; category renames update the saved selections, and archived categories remain available for historical reports.

The category filter includes archived categories so historical expenses remain reportable. Renaming a category updates its report templates. CSV exports use the selected template and current dates, with the columns for its chosen layout. Totals apply to matching transactions; mileage is labeled separately as all business miles in the date range.

Beside the report date controls, use Transaction amount ($) to choose Any amount, Above, Below, or Equal to a dollar value. Above and Below are strict comparisons; Equal to matches exact cents. Enter a nonnegative value with up to two decimal places. The filter compares individual transaction amounts before calculating category totals, and CSV exports include the same filter and its description. Like the dates, this is a per-run filter rather than part of a saved template. Mileage totals remain independent of the transaction amount filter.

## Setup on Windows

### Double-click Windows application

Open `dist\BusinessLedger\BusinessLedger.exe`. It includes a Java runtime, starts PostgreSQL from this project's `.local` folder, and shows the login window. PostgreSQL 17 must remain installed. Existing installations have a one-time database-password setup as described above.

Keep the entire `dist\BusinessLedger` folder together; the EXE depends on its `app` and `runtime` folders. You can create a Windows shortcut to the EXE. This build is configured for this project's absolute path; moving the project requires rebuilding. Closing the app leaves PostgreSQL running.

To update this portable executable after source changes, run `Update-Executable.ps1`; it preserves the JAR used by an open instance and selects the new version for the next launch. `Build-Executable.ps1` creates a separate application image in a unique release directory. `Build-Installer.ps1` creates the installable setup package. See `RELEASING.md` for the release workflow and verification limits.

### Quick start on this computer

Java and PostgreSQL 17 were found on this computer. Open PowerShell and run:

```powershell
cd 'C:\Programming\Business Ledger'
powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Ledger.ps1
```

The launcher downloads the Java database driver on its first run (internet required), compiles the code, and opens the same login flow as the executable. The app initializes PostgreSQL as needed. Maven is not required for this route. ExecutionPolicy Bypass applies only to that PowerShell process.

This uses a separate PostgreSQL cluster in `.local\postgres-data`, listening on 127.0.0.1 port 55432. Do not delete that folder: it contains your records. PostgreSQL keeps running after you close the app. To stop it cleanly:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe' stop -D 'C:\Programming\Business Ledger\.local\postgres-data' -m fast -w
```

Back up this cluster using pgAdmin or pg_dump with host 127.0.0.1, port 55432, user ledger_user. Include `business_ledger_profiles`, `business_ledger`, and every `ledger_profile_*` database. Do not copy its data directory while PostgreSQL is running.

### Manual setup / Maven alternative

Install a JDK 21 or newer, Apache Maven, and PostgreSQL 17. Build and launch from the project folder:

```powershell
mvn package
java -jar target/business-ledger-1.0.0.jar
```

The app manages its own local cluster and login profiles. The earlier manual `sql/setup.sql` script and database URL/user environment overrides are not used by the profile login flow. `LEDGER_DB_PASSWORD` may supply the existing local cluster password for migration; avoid retaining it in your environment afterward.

## Using the app

Enter dates as YYYY-MM-DD. Use positive amounts with at most two decimal places; select Income or Expense to determine the direction. Trips require a business purpose and positive mileage. Open Reports, choose the start/end dates, and run or export the report.

Select an income/expense transaction and click Edit selected to change its date, Income/Expense type, category, amount, payee/customer, notes, or account. Enter a required reason before saving; Cancel changes nothing. Archived account/category values can be retained, but new assignments must use active choices. Assign / change account uses the same editor and requires a reason. Checking/Savings/Credit account types are edited under Accounts & transfers → Edit account.

Changed transactions display Edited in the main ledger. View edit history shows each edit's timestamp, login name, reason, and before/after values. Updates and history save together; balances and reports recalculate. Unchanged records are not marked edited. Transfer rows remain managed on Accounts & transfers. History snapshots remain in the database after a transaction is deleted; the current history window opens from existing transaction rows.

Each date field has a calendar button with month navigation and a Today shortcut. The calendar opens at the date currently entered in its field. Cancel or Escape leaves the date unchanged. After saving a transaction, the date remains selected for the next entry; the last successfully saved transaction date is also remembered when you reopen the ledger. Mileage, transfers, and report dates have independent calendar controls.

The Transfer between accounts popup separately remembers the last successfully saved transfer date, including across restarts. Its calendar opens to that date. Canceling or failing to save does not change the remembered date. Existing ledgers without this preference start with the last entered transfer's date, or today if there are no transfers. Transfer and income/expense dates are remembered independently for each login's ledger.

Payee/customer and Notes offer gray inline completions in both new transactions and the edit window. Suggestions use case-insensitive prefixes from saved transactions in the current login's ledger, preferring newer transaction records. Tab accepts the complete value and advances to the next field; without a match, Tab advances normally. Escape dismisses the suggestion. Suggested text is not saved unless accepted; Enter still saves only the text actually entered. History refreshes after saved changes and is available again on reopening. Unsaved input and other profiles' records are not used.

## Scope and future additions

This is a starter cash ledger, not a full accounting or tax preparation system. It does not yet include receipts, bank imports, recurring entries, shared multi-user ledgers, refunds, or reconciliation. Food is recorded at its full paid amount; the application does not decide deductibility. Mileage is reported separately and is not included in expense totals or converted to a deduction. No tax mileage rates are hard-coded.

Back up PostgreSQL regularly with pgAdmin or `pg_dump` and test restoring the backup. CSV reports are summaries, not full database backups. The current interface loads all entries and performs database calls on its UI thread; pagination and background loading are follow-up work for larger datasets.

## Development verification

Compile without external dependencies (the PostgreSQL driver is needed at runtime):

```powershell
javac --release 21 -d target/classes (Get-ChildItem src/main/java/com/businessledger/*.java | ForEach-Object FullName)
```

Full runtime verification requires a running PostgreSQL instance and the JDBC driver supplied by the Maven build.
# Appearance

In **Settings > Open settings** (shortcut **Ctrl+,**), each ledger can choose **All income categories** or **Only this income category** (for example Paycheck). Click **Save settings** to persist the preference across sessions. It filters income in dashboard totals, income/expense charts, reports, and report CSV exports, including All In/Out; report category filters further narrow those results. Summaries name the active income scope. Expenses, original ledger records, running balances, account balances, and account balance charts continue to include all activity. Archived categories can still be selected for historical analysis. Renaming a category also updates the saved preference.

The **Dashboard** opens first and summarizes the signed-in ledger: income, expenses, net cash flow, business mileage, monthly income/expense bars, balances by account, and the top five expense categories. Choose dates or a month/quarter/year preset; the dashboard refreshes when you return to its tab. Income and expenses exclude transfers. Account balances use the Through date and include opening balances, earlier activity, and transfers. Archived accounts with nonzero balances are shown. All recorded dates includes mileage history. Summary cards wrap in narrower windows and the page scrolls vertically.

In **Settings > Open settings**, use the Theme selector to switch between Ocean, Forest, Plum, and Midnight (dark). Changes apply immediately, including charts and transaction row colors. The choice is remembered on this PC and used at the next startup, including login. Themes use the bundled Java interface and need no downloads.
