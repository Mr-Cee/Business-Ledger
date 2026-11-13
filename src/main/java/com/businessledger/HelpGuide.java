package com.businessledger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/** Offline, searchable instructions. Opening help never reads or changes ledger data. */
final class HelpGuide extends JPanel {
    record Topic(String title, String body) {
        @Override public String toString() { return title; }
    }

    static final List<Topic> TOPICS = List.of(
        new Topic("Recently deleted", """
            <h2>Recover an accidental deletion</h2>
            <p>Open <b>Settings &gt; Recently deleted</b>, or click <b>Recently deleted</b> in Income &amp; expenses. Select one entry, review its date, amount or miles, payee/purpose and account details, then click <b>Restore selected</b>. Scroll sideways for more columns. Cancel is the default confirmation choice.</p>
            <p>This covers income/expense transactions, transfers and mileage deleted after version 1.9.0 was installed. Entries remain available across restarts with no automatic expiry. Deletions from earlier versions cannot be recovered here; use an older backup if needed. Account, category and report-template removal are not part of this recovery list.</p>
            <p>Restoration preserves the original record ID and data and reconnects existing transaction/transfer edit history. Transfers restore both account effects together. Balances and reports include restored records again. Archived accounts/categories stay archived; category renames are followed when restoring. A record already using the original ID is never overwritten.</p>
            <p>A matching existing transaction triggers duplicate review. Approve only if the restored entry is separate and legitimate. New matches appearing during review stop restoration so you can review again. An entry can be restored only once; deleting it again creates a new recovery entry.</p>
            <p>Deletion and restoration each save atomically, including who deleted/restored the entry and when. Restored entries leave the pending list while their recovery metadata is retained. New backups include pending entries and restoration history and require version 1.9.0 or newer. Older backup formats remain supported but have no recovery list; restoring an older snapshot replaces the current recovery list too.</p>
            """),
        new Topic("Bulk editing", """
            <h2>Change several transactions together</h2>
            <ol><li>Open <b>Income &amp; expenses</b>. Filter or sort to find the transactions.</li><li>Use <b>Ctrl-click</b> for individual rows or <b>Shift-click</b> for a range. Only selected visible rows are included.</li><li>Click <b>Bulk edit selected</b>. Select Income/Expense transactions only; manage transfers separately on Accounts &amp; transfers.</li><li>Check <b>Change category</b>, <b>Change account</b>, or both. Choose active targets and enter a reason. Unchecked fields keep each row's original values.</li><li>Choose <b>Preview changes</b>. Review all selected IDs and their current/new values; scroll sideways for more columns. Already-correct rows show Unchanged.</li><li>Choose <b>Save changes</b> to apply the whole batch, or <b>Back</b> to adjust the choices. Canceling the editor writes nothing and keeps your selection.</li></ol>
            <p>Only category and account are changed. Date, type, amount, payee and notes are preserved. Each changed transaction receives its own before/after edit-history entry with your login and reason. Unchanged rows do not receive extra history.</p>
            <p>All changes save together. If a selected record changed or disappeared since loading, a target choice was archived, or any update/history write fails, the batch makes no changes. Refresh, select the records again, and review the new preview. Ledger balances and Dashboard refresh after a successful save.</p>
            <p>Individual edit, history and delete actions require exactly one selected row. Bulk editing is available in Income &amp; expenses.</p>
            """),
        new Topic("Duplicate detection", """
            <h2>Review possible duplicate transactions</h2>
            <p>Transactions match when date, Income/Expense type, amount, account, and full payee name match. Payee matching ignores capitalization and repeated whitespace. Category and notes may differ. Transfers are not compared. A match is a warning, not proof of an error.</p>
            <p>When adding a transaction manually, a warning lists the matching saved entries. <b>Cancel</b> is the default and keeps your form filled in. Choose <b>Add anyway</b> only for a separate legitimate transaction.</p>
            <p>CSV validation compares rows with the signed-in ledger and earlier records in the same file. The preview shows the matching ledger ID or earlier CSV record number. <b>Skip possible duplicates</b> is on by default.</p>
            <p>Click <b>Review duplicates</b> to see every flagged row, including rows beyond the first 200 preview entries. Check <b>Keep</b> for legitimate repeats you want imported; leave others unchecked to skip them. OK keeps your choices without importing. Cancel discards changes to the review. Returning to mapping and validating again resets Keep choices.</p>
            <p>Turning skipping off in mapping imports all valid rows after confirmation. Blank CSV payees can match other blank payees, so review those carefully. Imports check again immediately before saving: new matches that appeared after preview are skipped unless skipping is off. Final counts may differ from the preview. A failed import rolls back the complete batch.</p>
            """),
        new Topic("Payee rules", """
            <h2>Suggestions for familiar payees and customers</h2>
            <p>In <b>Income &amp; expenses</b>, enter a full payee/customer name. Matching ignores capitalization and extra spaces. Income and Expense have separate suggestions. A partial name does not match.</p>
            <p><b>Apply suggestion</b> copies the suggested category and account into the form. Review them before clicking Add transaction. Typing a name never changes your selections automatically. Suggestions do not change the date, amount, notes, or existing transactions, and do not run during imports or edits.</p>
            <p><b>Save payee rule</b> remembers the current category and account for this payee and type. Confirming replaces any existing rule for that name/type; it does not add a transaction. To change a rule, enter its payee/type, choose new values, and save again.</p>
            <p><b>Manage payee rules</b> lists saved rules and removes the selected rule. Removing a rule allows history-based suggestions again. Without a saved rule, the latest dated transaction with an active category and account supplies the suggestion (highest ID wins same-day ties).</p>
            <p>Saved rules with an archived account/category stay visible but cannot be applied until you restore the choice or replace the rule. Category renames preserve rules. Rules belong to this login's ledger and are included in backups. New backups require version 1.9.0 or newer; older version-1 backups can still be restored and contain no saved rules.</p>
            """),
        new Topic("Getting started", """
            <h2>Your first ledger, step by step</h2>
            <p>Business Ledger keeps a record of money you receive, money you spend, money you move between accounts, and business driving.</p>
            <ol><li>At the login window, click <b>Create account</b>. Choose an account name and a password of 12–256 characters, enter the password again, and confirm. Keep your password somewhere safe.</li>
            <li>Log in. A login is your separate ledger. A bank account such as Checking is added inside that ledger.</li>
            <li>Your first sign-in after creating a login opens <b>Accounts &amp; transfers</b> automatically. Set up your bank, cash, and credit accounts. Check any existing starter accounts before adding more. Select a row and use <b>Edit account</b> to set its name and opening balance. Later sign-ins open the Dashboard.</li>
            <li>Open <b>Income &amp; expenses</b>. Enter a date, choose Income or Expense, choose a category and account, and enter the amount. Click <b>Add transaction</b>.</li>
            <li>Open <b>Dashboard</b> to see your totals. Choose the date range you want to review.</li>
            <li>Open <b>Settings → Open settings</b> and make a backup under <b>Backup &amp; restore</b>.</li></ol>
            <p>Opening balances should be the amounts in your accounts <b>before</b> the transactions you plan to enter. Do not also enter that same starting money as income.</p>
            <p>Click a topic on the left for more instructions. Type a word such as “transfer” or “backup” in Search help to find relevant topics. This guide works without an internet connection.</p>
            """),
        new Topic("Using the app & common words", """
            <h2>Finding your way around</h2>
            <p>A <b>tab</b> is a screen name across the top, such as Dashboard or Reports. Click it once to open that screen. If tabs do not all fit, use the small arrows beside them or widen the window.</p>
            <p>A <b>dropdown</b> has a small arrow. Click the arrow and then an option. A <b>checkbox</b> turns an option on or off. A <b>selected row</b> is the highlighted line in a table: click the record once before using a button containing “selected.”</p>
            <p>A <b>category</b> says what money was for (for example, Paycheck or Supplies). An <b>account</b> says where the money is kept (for example, Checking). A <b>payee</b> is someone you paid; a <b>customer</b> is someone who paid you.</p>
            <p>Click a table heading to sort it. Scroll to see more rows or columns. Drag the divider between two panels to give one more room. A blank screen may simply mean the selected dates or filters have no records.</p>
            <p><b>Save / Add / Import</b> buttons commit your entries. Typing into a form alone does not add a record. <b>Cancel</b> closes that operation without submitting it. Deleting a saved record is different from canceling: back up first if you may need it later.</p>
            <p>Press <b>Tab</b> to move between controls. <b>F1</b> opens help for the main screen. <b>Ctrl+,</b> opens Settings. <b>Ctrl+F</b> focuses ledger search on Income &amp; expenses. Hover means leave the mouse pointer over something without clicking; charts then show more information.</p>
            """),
        new Topic("Login & separate ledgers", """
            <h2>Opening the right ledger</h2>
            <p>Select your <b>Account name</b>, enter your <b>Password</b>, and click <b>Log in</b>. Passwords are case-sensitive: capital and lowercase letters differ.</p>
            <p><b>Create account</b> creates a separate login and ledger. Enter a name, a password of 12–256 characters, and the same password in Confirm password. Additional logins start with their own empty ledger. On a PC with an older existing ledger, the first login can inherit it; read the message in the create-account window.</p>
            <p>A login account and a bank account are different. To add Checking or Savings, log in first and use <b>Accounts &amp; transfers</b>.</p>
            <p>Backups contain ledger data, not your login accounts or passwords. On another PC, create a login first, log in, then restore the backup into that ledger. Restore replaces the signed-in ledger's data.</p>
            """),
        new Topic("Dashboard", """
            <h2>Your overview</h2>
            <p>The Dashboard summarizes the signed-in ledger. Choose dates to see income, expenses, net cash flow, mileage, monthly income and expenses, and the largest expense categories. It refreshes when you return to this tab.</p>
            <ul><li><b>Income:</b> money received that matches your income preference in Settings.</li>
            <li><b>Expenses:</b> money spent. Transfers are excluded from income and expenses.</li>
            <li><b>Net cash flow:</b> income minus expenses. A negative value means expenses exceeded the counted income.</li>
            <li><b>Mileage:</b> recorded business miles in the chosen dates.</li>
            <li><b>Account balances:</b> balances as of the selected end date, including opening balances, all income and expenses, and transfers.</li>
            <li><b>Estimated taxes due:</b> current calendar-year income multiplied by your saved tax percentage (24% by default). This card ignores the Dashboard date range but follows your income category preference.</li></ul>
            <p>The tax card is a simple planning estimate. It does not subtract expenses, withholding, or tax payments. Change its percentage in <b>Settings → Open settings → Estimated taxes</b>.</p>
            <p>When a newer app version is found, a <b>red exclamation mark</b> and <b>Update available</b> message appear above the date controls. Click <b>View update</b> to reopen the download prompt. Choosing Later keeps this reminder visible for the rest of the session.</p>
            <p>Hover over chart marks to see exact amounts. Read the Date ranges topic if you want a particular month or quarter.</p>
            """),
        new Topic("Income & expenses", """
            <h2>Add money received or spent</h2>
            <ol><li>Choose the <b>Date</b> using the calendar button, or type it as year-month-day, such as 2026-01-15. The form remembers the last successfully added transaction date; check it each time.</li>
            <li>Choose <b>Type</b>: Income for money received, Expense for money spent.</li>
            <li>Choose a <b>Category</b>. Selecting Income initially chooses Paycheck; change it if needed.</li>
            <li>Enter <b>Amount ($)</b> as a positive number, such as 45.50. Type determines whether money goes in or out.</li>
            <li>Enter <b>Payee / customer</b> and optional <b>Notes</b>. Choose the <b>Account</b> affected.</li>
            <li>Click <b>Add transaction</b>. The saved row appears in the table. Search filters can hide it until cleared.</li></ol>
            <p>This tab focuses <b>Payee / customer</b> when opened. Type a name; <b>Tab</b> accepts an autocomplete suggestion and stays in the field, then the next <b>Tab</b> moves to Amount. If there is no completion, Tab moves directly to Amount. <b>Ctrl+Enter</b> applies the suggested category/account without saving or moving focus. Plain <b>Enter</b> adds only when payee and amount are filled in and the transaction is valid. After saving, focus returns to Payee / customer for the next entry.</p>
            <p>In Notes, <b>Tab</b> accepts the suggestion and moves to Add transaction. Its Enter shortcut uses the same payee and amount requirements. Review date, type, category and account before submitting.</p>
            <h3>Buttons and table options</h3>
            <p>Use Ctrl-click or Shift-click to select several Income/Expense rows, then <b>Bulk edit selected</b> to change their category/account with a shared reason and per-row edit history. See <b>Bulk editing</b> for the preview and save steps. Individual record actions require one selected row.</p>
            <p>When an entry matches an existing date, type, amount, account and payee, review the duplicate warning before choosing Cancel or Add anyway. See <b>Duplicate detection</b> for matching and CSV review details.</p>
            <p><b>Manage categories</b> opens the category list: Add creates a category, Rename changes the selected name, and Remove from list hides a category from new-entry choices while keeping its history. Close returns to the ledger. <b>Import CSV</b> brings in a file using a mapping and preview step. <b>Edit selected</b> corrects a highlighted transaction and requires a reason. <b>Assign / change account</b> opens the same edit form to correct the account. <b>View edit history</b> shows who changed it, when, why, and before/after values. The Edited column marks changed records. <b>Delete selected</b> removes the highlighted record after confirmation.</p>
            <p><b>Color rows</b> highlights money out in red and money in in green and remembers your choice. The <b>Running balance</b> is the account's balance at that point in date order; filters do not recalculate it using only visible rows. Unassigned entries have no account running balance.</p>
            <p>Transfer In and Transfer Out rows belong to one transfer. Edit or delete them on <b>Accounts &amp; transfers</b>, rather than changing one side here.</p>
            """),
        new Topic("Search & filters", """
            <h2>Find a transaction</h2>
            <ol><li>Open <b>Income &amp; expenses</b>. Click the search box, or press <b>Ctrl+F</b>.</li>
            <li>Type a payee, notes, category, account, or other ledger text. The table updates as you type. Punctuation is treated as text.</li>
            <li>Click <b>More filters</b> for additional choices, including category, account, dates, and amounts. Filters work together: a row must match all active conditions.</li>
            <li>Read the matching-record count. If nothing matches, remove a condition or click <b>Clear filters</b>.</li></ol>
            <p><b>Clear filters</b> returns to all records. Hiding the extra filter controls is not the same as clearing your filters. Searching never deletes or changes records. Running balances continue to include all activity.</p>
            <p>To correct a result, click its row, then <b>Edit selected</b>. To change a transfer, use Accounts &amp; transfers.</p>
            """),
        new Topic("Accounts & transfers", """
            <h2>Accounts: where your money is</h2>
            <p>The upper table lists accounts, types, opening balances, recorded balances, and status. <b>Add account</b> creates one. To correct its name, type, or opening balance, click its row and then <b>Edit account</b>.</p>
            <p>An <b>Opening balance</b> is what was in the account before your recorded transactions. For a credit account, enter money owed as negative (for example, -500.00). Recorded balances include all saved activity, including future dates; they can differ from a Dashboard balance limited to an end date.</p>
            <p><b>Delete / archive account</b> removes an unused account. Accounts with history or a nonzero opening balance are archived so that history remains. Archived accounts cannot receive new entries; select one and click <b>Restore account</b> when you need to use it again.</p>
            <h2>Transfers: moving your own money</h2>
            <ol><li>Click <b>Transfer between accounts</b>.</li><li>Choose the date, the account money comes <b>from</b>, and the account it goes <b>to</b>.</li><li>Enter a positive amount and any notes, then confirm. The accounts must be different.</li></ol>
            <p>For example, moving $100 from Checking to Savings is one transfer. Paying a credit card from Checking is also a transfer; record the original purchase as an expense, rather than counting the card payment as another expense.</p>
            <p>The transfer date remembers the last saved transfer separately from the transaction date. Check it before saving. Each transfer appears in transfer history and as two ledger rows. It changes account balances but is excluded from income and expense reports.</p>
            <p><b>Edit selected transfer</b> changes its date, amount, accounts, or notes and requires an edit reason. Both sides update together. <b>View transfer edit history</b> shows the changes. <b>Delete selected transfer</b> removes the transfer and its effect on both accounts after confirmation.</p>
            """),
        new Topic("Account ledgers", """
            <h2>Review one account at a time</h2>
            <p>On <b>Dashboard</b>, click an account name or balance. You can also select its row and click <b>View selected ledger</b> or press Enter. The ledger starts with the Dashboard's dates, so its closing balance matches the balance you clicked.</p>
            <p>On <b>Accounts &amp; transfers</b>, select an account and click <b>View account ledger</b>, or click its recorded balance. This opens all recorded dates, including future entries. Archived accounts can also be reviewed.</p>
            <p>The top shows the balance before the period, money in, money out, and the balance at the end. Transfers and every income category are included because this is the account's actual history. A credit account can have a negative balance when money is owed.</p>
            <p>Use <b>Search ledger</b> (Ctrl+F), Type, and <b>More filters</b> to find records. Date filters change the period balances. Search, type, category, and amount filters only hide rows; they do not change the actual account balance or historical running balances. <b>Clear filters</b> shows this account's full history again.</p>
            <p><b>Refresh ledger</b> reloads the account's history. Select a row and choose <b>Edit selected</b> to correct a transaction or transfer with the existing required edit reason. Transfer changes update both accounts. <b>View edit history</b> opens that record's audit history. Scroll sideways for notes and other details if the window is narrow.</p>
            <p>Use <b>Income &amp; expenses</b> to add a new transaction, choosing this account in its Account dropdown. Click <b>Close</b> or press Escape to return to the main window.</p>
            """),
        new Topic("Business mileage", """
            <h2>Record a business trip</h2>
            <p>Use this screen to keep a log of business driving.</p>
            <ol><li>Choose the trip <b>Date</b> (required).</li><li>Optionally enter <b>From</b> (where you started), <b>To</b> (where you went), and <b>Business purpose</b>. You can leave any or all three blank.</li><li>Enter <b>Miles</b> as a positive distance (required), then click <b>Add trip</b>.</li></ol>
            <p>Review the trip in the table after saving. To remove a mistaken record, select its row and click <b>Delete selected</b>. Record the corrected trip if needed. Accidental deletions can be restored from Settings &gt; Recently deleted.</p>
            <p>Mileage is a distance record, not a money transaction. Recording miles does not automatically add an expense or change your bank balance. The Dashboard mileage card uses the selected dates.</p>
            """),
        new Topic("Date ranges", """
            <h2>Choose the period you want to see</h2>
            <p>Dashboard, Reports, and Charts have date range controls. A range includes both its first and last date.</p>
            <ul><li><b>This / last month, quarter, or year:</b> chooses a calendar period relative to today's date.</li><li><b>Month:</b> choose the month and year.</li><li><b>Q1–Q4:</b> choose the quarter and year. Q1 is January–March; Q2 April–June; Q3 July–September; Q4 October–December.</li><li><b>Year:</b> choose a calendar year.</li><li><b>All recorded dates:</b> includes the recorded date span.</li><li><b>Custom dates:</b> enter your own From and Through dates or use the calendar buttons. Editing a date switches to Custom dates.</li></ul>
            <p>In a calendar, use the left and right arrows to change months, then click a day. <b>Today</b> chooses today's date. <b>Cancel</b> leaves the date unchanged. Typed dates use YYYY-MM-DD, for example 2026-11-05.</p>
            <p>Presets cover full calendar periods, which may include future dates. On Reports, click <b>Run report</b> after choosing the range. Charts refresh when range choices change; use <b>Refresh charts</b> if you need to refresh the display. Changing a range does not change saved transactions.</p>
            """),
        new Topic("Reports", """
            <h2>Create a table of totals or records</h2>
            <ol><li>Choose a report template.</li><li>Choose the date range. Optionally set the amount comparison and amount.</li><li>Click <b>Run report</b>.</li><li>Read the result and its summary. Click <b>Export report CSV</b> to save a report file; choose a folder and file name.</li></ol>
            <p>Default templates include <b>All Expenses</b>, <b>All Income</b>, <b>All Transactions</b>, <b>All Food</b>, and <b>All In/Out</b>. All In/Out shows one row per account with total income and total expenses, without listing every transaction. Opening balances and transfers are excluded.</p>
            <p><b>New template</b> creates a reusable report definition. <b>Edit template</b> changes the selected definition. <b>Delete template</b> removes that definition after confirmation. A template can limit the type, categories, or account and choose detailed records or grouped totals. Deleting a template does not delete ledger transactions.</p>
            <p><b>Any amount</b> applies no amount condition. <b>Above</b>, <b>Below</b>, and <b>Equal to</b> compare against the entered amount. Check this choice if records seem missing.</p>
            <p>Income in reports and exports follows your income category preference in Settings. Expenses are counted normally. Transfers are excluded even from All Transactions: use the ledger or transfer history to review them. CSV is a spreadsheet-friendly text file, not a complete ledger backup.</p>
            """),
        new Topic("Charts", """
            <h2>See patterns in your data</h2>
            <p>Choose a chart from the dropdown, choose dates, and use <b>Refresh charts</b> when needed. Hover over a bar, point, pie slice, or legend entry to see its details. The table below gives exact values and can be sorted by clicking headings.</p>
            <ul><li><b>Income vs expenses:</b> compare money received, money spent, and net cash flow.</li><li><b>Expenses by category:</b> see what you spend money on.</li><li><b>Monthly income &amp; expenses:</b> green and red bars are paired for each month, with a net figure. Groups wrap in narrower windows.</li><li><b>Account balances over time:</b> use Account to choose one account or All accounts. Choose Daily, Monthly, or Yearly for closing balances at that interval. Earlier activity is carried forward. Opening balances and transfers are included.</li><li><b>Top payees (pie):</b> see the eight largest payees by expenses; the rest are grouped into Other payees. Hover shows the name, amount, percentage, and expense count.</li><li><b>Account transfers:</b> By account compares incoming and outgoing money and net transfers; Between accounts shows directed routes such as Checking to Savings. The overall total counts each transfer once.</li></ul>
            <p>Income and expense charts exclude transfers. Income follows Settings. Balance and transfer charts include transfers so you can follow actual money movement. Balance charts can show a negative credit balance when money is owed.</p>
            <p>If the chart is empty, expand the dates or check for records. Widen the window or scroll down to see more controls and the values table.</p>
            """),
        new Topic("Import CSV", """
            <h2>Bring in transactions from a file</h2>
            <p>A CSV file is a text file of rows and columns, often offered as a bank or spreadsheet export. Import CSV adds transactions; it does not restore a full ledger. Make a backup before a large import.</p>
            <ol><li>On <b>Income &amp; expenses</b>, click <b>Import CSV</b> and choose your .csv file.</li><li>Match the file's columns to the ledger's fields: date, amount, type, category, payee/customer, notes, and account as appropriate. Leave unwanted columns unmapped; they are ignored.</li><li>Choose the date format and amount style that match the file. Signed amounts and separate debit/credit columns are supported. Check whether positive values really mean income in your export.</li><li>Choose defaults for fields missing from the file, including category or account. Review category creation and duplicate-skipping options.</li><li>Click <b>Validate &amp; preview</b>. Check dates, Income/Expense types, amounts, and accounts against the original file. Resolve reported row errors.</li><li>Only when the preview is correct, click <b>Import transactions</b>. Read the result before closing.</li></ol>
            <p>Nothing is saved until you import. Changing the mapping can change the meaning of every row; validate again after changes. Duplicate skipping can help avoid repeated entries, but review the preview yourself. Do not import the same bank activity twice.</p>
            <p>Do not import transfers as ordinary income and expenses. Record money moved between your own accounts with Transfer between accounts so analysis totals stay meaningful.</p>
            """),
        new Topic("Settings & appearance", """
            <h2>Make the app work your way</h2>
            <p>Click <b>Settings → Open settings</b>, or press <b>Ctrl+,</b>. Scroll inside the dialog to see all sections. The Save settings button remains at the bottom.</p>
            <ul><li><b>Appearance:</b> choose Ocean, Forest, Plum, or Midnight (dark). Theme changes apply and save immediately on this PC.</li><li><b>What counts as income?</b> choose All income categories, or Only this income category and a category such as Paycheck. Click Save settings. This affects Dashboard totals, income/expense charts, reports, and report CSV exports.</li><li><b>Estimated taxes:</b> enter a percentage from 0 to 100, including decimals, then Save settings. Default is 24. The estimate uses current calendar-year income and your income category choice. It does not subtract expenses or tax payments.</li><li><b>Backup &amp; restore:</b> make or restore backups, and choose an automatic backup folder. See the backup topic before restoring.</li></ul>
            <p>Income preferences, tax rate, and backup choices are remembered for the signed-in ledger. Account balances and ledger rows always include all activity, even income excluded from analysis. Choosing Paycheck does not remove other income records.</p>
            <p>Click <b>Save settings</b> and check the saved message before closing. <b>Close</b> closes the dialog; it does not save unsaved income, tax, or backup choices. Theme changes are already saved immediately.</p>
            """),
        new Topic("Backup, restore & moving PCs", """
            <h2>Save a recovery copy</h2>
            <ol><li>Open <b>Settings → Open settings</b> and scroll to <b>Backup &amp; restore</b>.</li><li>Click <b>Back up ledger...</b>.</li><li>Choose a folder and file name. Keep the .ledgerbackup ending. Wait for the success message.</li><li>Keep another copy somewhere safe, such as an external drive. A copy on the same PC cannot protect against losing that PC.</li></ol>
            <p>Backups include the signed-in ledger's transactions, accounts, categories, transfers, mileage, edit history, report templates, and saved ledger preferences. They also include deleted-record recovery entries and restoration history. They do not include login accounts or passwords. Treat backup files as private financial information.</p>
            <h3>Automatic backups</h3>
            <p>Check <b>Back up this ledger when closing the app</b>, click <b>Choose folder...</b>, and choose a destination you can write to. Click <b>Save settings</b>. Close the app normally with its X button so it can run the backup. Force-closing through Task Manager can bypass it. If a backup fails, read the message and make a manual backup.</p>
            <h3>Restore a backup</h3>
            <p><b>Restore replaces the data in the ledger you are currently signed into; it does not merge records.</b></p>
            <ol><li>Check that you are signed into the intended destination ledger.</li><li>Click <b>Restore ledger...</b> and choose the .ledgerbackup file.</li><li>Read the confirmation and safety-backup information carefully. Continue only if replacing this ledger is what you want.</li><li>Wait for completion, then check your accounts, transactions, and settings.</li></ol>
            <p>The app creates a safety backup before replacing data. If you chose the wrong backup, keep that safety copy and restore it to recover the earlier ledger. A damaged backup is rejected without completing the replacement.</p>
            <h3>Move to another PC</h3>
            <p>Make a backup on the old PC and copy it to the new PC. Install Business Ledger, create a login, log in, and restore. The installer bundles Java and PostgreSQL. The application folder holds the program; the data folder holds your ledgers. A CSV export is not a substitute for .ledgerbackup.</p>
            """),
        new Topic("Fixing problems & getting help", """
            <h2>Things to check first</h2>
            <ul><li><b>“Select a record first”:</b> click the relevant table row once, then click the action button.</li><li><b>A new entry is missing:</b> clear ledger filters, check the selected account and date, and confirm you clicked Add transaction.</li><li><b>A report or chart is empty:</b> check dates, amount filters, report template, selected account, and income preference. Then refresh or run the report.</li><li><b>Income seems too low:</b> check whether Settings counts only one category. Transfers never count as income.</li><li><b>A balance differs from the bank:</b> check opening balance, account assignments, missing or repeated entries, transfers, and future dates. Dashboard balances use an end date; the accounts table includes all recorded dates.</li><li><b>A date or amount is rejected:</b> use YYYY-MM-DD for dates and a positive transaction amount with at most two decimal places. Opening credit balances can be negative.</li><li><b>A dropdown or button is out of view:</b> enlarge the window, scroll, or use the navigation arrows. Controls may wrap to another row.</li><li><b>Startup fails:</b> note the complete error and the data folder you chose. Do not delete data folders to try to repair it.</li></ul>
            <p>Before a large correction, make a backup. To fix a saved transaction, use Edit selected and explain the reason. For a transfer, use Edit selected transfer.</p>
            <h3>Get a newer version</h3>
            <p>The app checks GitHub after login. You can also click <b>Help → Check for updates...</b>. Choose <b>Download update</b> to download inside Ledger, <b>View release notes</b> to read what changed in your browser, or <b>Later</b> to keep working. A progress bar shows the download; Cancel stops it. The app verifies the installer before offering <b>Install and restart</b>.</p>
            <p><b>Install and restart</b> completes your automatic backup if enabled, closes Ledger, installs the newer version, and reopens the login window. Your existing data folder and records are kept; sign in again afterward. If you cancel the backup or the installer cannot start, Ledger stays open. Choosing Later after a download keeps it in temporary storage across restarts; use the Dashboard reminder to return to it. Ledger verifies and reuses the saved installer instead of downloading it again. A saved download is offered as Use downloaded update. If downloading fails, continue working and try again later. Updating does not require restoring a backup.</p><p>On the newly installed version\u0027s startup, background cleanup removes cached installers and receipts for that version and older versions. It retries while Windows still holds an installer open, and can retry again on a later app start. Newer uninstalled updates and unrelated files are preserved. Windows may clear temporary files; if a saved file is missing or damaged, Ledger downloads a verified replacement. Release discovery still checks GitHub.</p>
            <p>For a support request, open <b>Help → About Business Ledger</b> to find your version and data folder. Include what you clicked and the exact error message. Remove passwords and private financial details from screenshots before sharing them.</p>
            """)
    );

    private final JTextField search = new JTextField();
    private final DefaultListModel<Topic> matches = new DefaultListModel<>();
    private final JList<Topic> topics = new JList<>(matches);
    private final JEditorPane article = new JEditorPane("text/html", "");
    private final JLabel count = new JLabel();

    HelpGuide(String title) {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel label = new JLabel("Search help:"); label.setLabelFor(search);
        search.getAccessibleContext().setAccessibleName("Search help topics and instructions");
        JPanel header = new JPanel(new BorderLayout(8, 4));
        header.add(label, BorderLayout.WEST); header.add(search); header.add(count, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        article.setEditable(false);
        article.setFont(UIManager.getFont("Label.font").deriveFont(15f));
        article.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        article.getAccessibleContext().setAccessibleName("Help instructions");
        topics.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        topics.getAccessibleContext().setAccessibleName("Help topics");
        topics.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) display(topics.getSelectedValue()); });
        JScrollPane navigation = new JScrollPane(topics);
        navigation.setMinimumSize(new Dimension(150, 100));
        JScrollPane page = new JScrollPane(article); page.setMinimumSize(new Dimension(220, 100));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, page);
        split.setResizeWeight(0.27); split.setDividerLocation(235); add(split);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        filter(); select(title);
    }

    static List<Topic> find(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return TOPICS.stream().filter(t -> (t.title() + " " + t.body().replaceAll("<[^>]*>", " ")
            .replace("&amp;", "&")).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private void filter() {
        Topic prior = topics.getSelectedValue(); matches.clear();
        find(search.getText()).forEach(matches::addElement);
        count.setText(matches.size() + " topics — choose a topic to read its instructions");
        if (prior != null && matches.contains(prior)) topics.setSelectedValue(prior, true);
        else if (!matches.isEmpty()) topics.setSelectedIndex(0);
        else display(null);
    }

    private void select(String title) {
        search.setText("");
        for (int i = 0; i < matches.size(); i++) if (matches.get(i).title().equals(title)) {
            topics.setSelectedIndex(i); topics.ensureIndexIsVisible(i); return;
        }
        topics.setSelectedIndex(0);
    }

    private void display(Topic topic) {
        Color fg = UIManager.getColor("TextArea.foreground"), bg = UIManager.getColor("TextArea.background");
        if (fg == null) fg = Color.BLACK;
        if (bg == null) bg = Color.WHITE;
        article.setForeground(fg); article.setBackground(bg);
        String style = "body { margin: 12px; color: " + hex(fg) + "; background-color: " + hex(bg)
            + "; } p, li { margin-top: 8px; } h2 { margin-top: 4px; }";
        article.setText("<html><head><style>" + style + "</style></head><body>"
            + (topic == null ? "<h2>No matching topics</h2><p>Try a shorter word, such as date, account, or backup. Clear Search help to show all topics.</p>" : topic.body())
            + "</body></html>");
        article.setCaretPosition(0);
    }

    private static String hex(Color color) { return String.format("#%06x", color.getRGB() & 0xffffff); }

    static void bind(JRootPane root, java.util.function.Supplier<String> topic) {
        root.registerKeyboardAction(e -> show(SwingUtilities.getWindowAncestor(root), topic.get()),
            KeyStroke.getKeyStroke("F1"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    static void show(Window owner, String topic) {
        for (Window window : owner.getOwnedWindows()) {
            if (window instanceof JDialog dialog && dialog.getContentPane() instanceof HelpGuide guide) {
                guide.select(topic); dialog.setVisible(true); dialog.toFront(); return;
            }
        }
        JDialog dialog = new JDialog(owner, "Business Ledger — Help", Dialog.ModalityType.MODELESS);
        HelpGuide guide = new HelpGuide(topic);
        JButton close = new JButton("Close help"); close.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT)); footer.add(close);
        guide.add(footer, BorderLayout.SOUTH); dialog.setContentPane(guide);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen = owner.getGraphicsConfiguration().getBounds();
        dialog.setSize(Math.min(980, screen.width - 60), Math.min(720, screen.height - 80));
        dialog.setMinimumSize(new Dimension(Math.min(500, dialog.getWidth()), Math.min(350, dialog.getHeight())));
        dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
    }
}
