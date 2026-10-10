---
title: Usage guide
nav_order: 1
---

# Tallybook usage guide

Tallybook tracks what you spend and earn, wallet by wallet, on your own phone. This page takes each part of the app in the order you are likely to meet it. Quick answers are in the [FAQ](FAQ.md), and the import file layout is in [CSV import format](CSV.md).

Most screens open from the navigation drawer, reached from the icon in the toolbar. **Settings, User interface, Drawer entries** hides sections you never use. Transactions and Settings always stay.

## First launch and wallets

The first screen asks whether this is your first time. **First time** shows a short introduction and then opens **New wallet**. **Restore backup** opens the backup services instead. Once the first wallet is saved, the app adds a starter set of categories.

A wallet has a name, a currency, an **Initial amount** and an optional **Group**. To switch wallets, open the navigation drawer and tap the wallet name at its top. The drawer swaps its sections for your wallets, grouped under their group names. Tap one to make it current. Most screens show only the current wallet.

- The last entry, **Total**, adds up every wallet that has **Show this wallet in the total count** ticked.
- **Manage wallets**, at the bottom of the list, is where you edit, sort, archive or delete wallets. An archived wallet leaves the drawer but still counts toward Total.

## Adding a transaction or a transfer

On **Transactions**, the round + button opens **New transaction** on the Transactions tab and **New transfer** on the Transfers tab. A transaction needs a category, a date and a wallet. A transfer moves money **From** one of your wallets **To** another, with an optional **Tax**. The category decides whether money comes in or goes out, which is why the keypad refuses a minus sign ([FAQ](FAQ.md)).

- A transaction dated in the future is saved, but the transactions list and the balance leave it out until its date arrives. The app says so when you save.
- Long press the Tallybook icon on your home screen for **New transaction** and **New transfer** shortcuts.

## Categories

**Categories** has three tabs, Incomes, Expenses and System. The + button adds a category, and **Sort** in the toolbar reorders them.

- A category can have a **Parent category** of the same type, one level deep only.
- **Settings, Utilities, Automatic categories** fills in the category of a new transaction when its description contains a rule's text, ignoring case. A category you picked yourself is never replaced.

## The transactions list and the day strip

**Transactions** is the start screen, and Back returns to it. It lists the current wallet's transactions in periods, each under a header with its totals. **Settings, User interface, Grouping** sets the period length. Tap a transaction for Edit, Delete and, in its menu, **Duplicate**. Tap a header for charts ([FAQ](FAQ.md)), or its arrow to fold the period away.

- Long press a transaction to start a selection, then tap more. The toolbar offers **Delete**, **Move to wallet** and **Change category** for all of them. The two sides of a transfer cannot be selected.
- **Calendar** in the toolbar opens a month row above a day strip. Days with transactions are marked, and tapping a day lists them.

## Search

**Search** is in the toolbar of **Transactions**. You build a search from criteria: Category, Text, People, Status, Wallet, Amount and Date.

- Text looks in the description, note, event and place.
- The **All** chip means every criterion must match. Tap it for **Any**, where one is enough.

## Overview

**Overview** shows the current wallet's money period by period. **Advanced settings** in its toolbar sets the dates, the grouping, and whether it shows **Cash flow** or one **Category**. Tapping a period opens the same charts as a transactions header.

## Budgets

**Budgets** has two tabs, Running and Expired. A budget has a **Type** (Incomes, Expenses or Category), dates and wallets.

- Tick **Repeat this budget** and pick a **Recurrence**. When a period ends, the app opens the next one with the same settings, and the old period stays under Expired.
- One budget can cover several wallets if they share a currency.

## Savings

**Savings** has two tabs, Running and Completed. A saving is a goal amount, with **Deposit** and **Withdraw** on its card. Spending from one is in the [FAQ](FAQ.md).

- Once a saving reaches its goal, its card offers **Withdraw everything** instead.

## Debts

**Debts** has two tabs, Debt for money you owe and Credit for money owed to you. The + button creates the kind on the tab you are viewing. Cards offer **Pay** and **Pay in full**, or **Receive** and **Receive in full**.

- Saving a new one asks whether to add a master transaction. Yes records the borrowed money arriving in the wallet, or the lent money leaving it. No leaves the balance alone.
- **Show finished only** in the toolbar switches both tabs to settled debts.

## Recurrences

**Recurrences** has two tabs, Transactions and Transfers. You fill one in like a transaction and set its **Recurrence**. When one comes due, the app adds it and tells you in a notification.

- Deleting a recurrence keeps the transactions it already added.

## Models

**Models** saves a transaction or transfer you enter often. **Add** on its card saves it at once, dated now, with **Undo** in the message that follows. **Edit** opens a new entry filled in from it, to change before saving.

## Events

**Events** has two tabs, Running and Completed. An event has a **Start date** and **End date**. Link a transaction by picking the event in its **Event** field. **Show transactions** in an event's toolbar lists them.

## Places

**Places** lists the places you attach to transactions, and **Map** in its toolbar shows them. In a place's editor, the icon beside **Address** picks the spot on a map.

- Maps load from the default OpenStreetMap servers. **Settings, Utilities, Tile server** takes another https tile server.

## People

**People** lists who you attach to transactions. A person's toolbar has **Show transactions**, and its menu has **New transaction**, which opens one with that person already added.

## Backup and restore

**Settings, Database, Backup services** offers External Memory, Local folder and WebDAV. Connect one, then the round + button creates a backup and tapping a backup file restores it. Automatic backups are in the [FAQ](FAQ.md).

- A backup can carry a password. Leave the field empty for none.
- The **Auto backup** settings can also write your transactions as a CSV file, which is not password protected.

## Import and export

**Settings, Database, Export data** writes CSV, XLS or PDF for the dates and wallets you choose. **Settings, Database, Import data** reads CSV only.

- A CSV export leaves out both sides of every transfer, because nothing in the file could pair them again. A transfer fee is still written, and XLS and PDF include transfers in full.

## The lock

**Settings, Utilities, Access protection** offers Pin code, Sequence, and Fingerprint on phones that support it.

- The lock asks again whenever you come back after more than a second away.
- While it is on, the notification for an added recurrence leaves out the description.

## The widget

Add **Wallet balance** from your home screen's widgets and choose a wallet. It shows the balance and a button that adds a transaction to that wallet.

- With the lock on, it shows **Tallybook is locked** unless you ticked **Show the balance even when the app is locked**.

## Settings and utilities

**Settings** has User interface, Utilities, Database and About. The navigation drawer also holds **Calculator** and **Converter**.

- **Settings, User interface, Open the calculator first** starts each new transaction on the calculator, and **Default wallet** picks the wallet it starts in.
- The Converter shows **Unknown** until it has a rate. **Settings, Utilities, Update exchange rates** downloads them.
