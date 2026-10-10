# Tallybook FAQ

Answers to the questions people actually ask. Every path below is written the way the app spells it.

## Why will the keypad not take a negative amount?

Because the category already says which way the money goes.

Tallybook works out income or expense from the category you pick, not from the sign of the amount. A minus you type is a second answer that contradicts the first, so the keypad refuses it, leaves the amount on the display and tells you. A wallet's starting balance is the exception and does take a negative, since a credit card starts below zero.

Before 1.4.0 the keypad accepted it and stored the amount as typed, which could move a total the wrong way. If you are seeing that, update.

CSV import works the other way round. There is no category picker in a file, so the importer reads the sign to decide the direction, and `money` has to be negative for an expense. That format is on the [CSV import format](CSV.md) page.

## Where are the charts?

In the transactions list, tap the date header above the transactions, the one naming the period you are looking at. It opens three tabs: Incomes, Expenses and Summary.

## Is my CSV or XLS export a backup?

No. Use Settings, Database, Backup services for anything you would be upset to lose.

A backup holds everything and can be restored into an empty app. An export writes a file for reading somewhere else. Tallybook exports CSV, PDF and XLS, and of those it can only read CSV back. Importing a CSV brings in transactions, creating the wallets, categories, places and people it needs along the way, and a wallet it creates starts from zero. It does not bring back your budgets, savings, debts or recurrences.

Restoring a backup replaces everything currently in the app, so it is not a merge.

## How do I back up automatically?

Settings, Database, Backup services, then pick where the backups go. Open the menu in the toolbar on that screen and choose Auto backup.

You can set how often it runs, restrict it to WiFi, and skip a run when nothing has changed. Choose the folder before you turn it on.

There is no built in sync between devices. Point a backup at a folder your own sync tool watches, or at your WebDAV server, which Tallybook supports directly. Local folder can also save to Google Drive or Dropbox, since their apps show up in its folder picker, and automatic backups work there too.

## The currency I want is not in the list

Settings, Utilities, Manage currencies. You can add anything, including a cryptocurrency, giving it a name, an ISO code and anywhere from 0 to 8 decimal places.

## How do I edit or delete a category?

Tap the category to open it. Edit and Delete are in the toolbar, next to the action that shows the transactions filed under it.

## How do I spend money I saved up?

Two steps, on purpose. Withdraw from the saving into a wallet, then spend from that wallet.

A saving is a place money sits, not a wallet you can pay from, so nothing leaves it directly. You cannot withdraw more than the saving holds.

## What are the system categories for?

Tallybook files its own bookkeeping under them. Transfer and Transfer tax appear when you move money between wallets. Debt, Debt paid, Credit and Credit paid come from the Debts screen. Deposit and Withdraw come from Savings.

They are not offered when you enter a transaction, so you cannot file something under one yourself. They are offered in the Overview filter, where you can include or exclude them.

They show up in your lists because they are real transactions. Nothing you do creates them except the screen that owns them.

## What else is in there?

Worth a look if you have never opened them:

- **Events** group transactions by something that happened, a trip or a move, across whatever period it ran.
- **Places** attach a location to a transaction, with a map.
- **People** record who you spent it with, or who owes you.
- **Models** save a transaction you enter often, ready to reuse.
- **Recurrences** post a transaction on a schedule.
- **Calculator** and **Converter** sit in the menu next to them.
- **Daily reminder** in Settings, Utilities nudges you to write the day down.

## Which language does the app use?

The one your phone is set to. On Android 13 and later you can give Tallybook its own language in the system settings, under Tallybook, Language.

## Something here is wrong or missing

Open an issue: https://github.com/herrerad85/tallybook/issues
