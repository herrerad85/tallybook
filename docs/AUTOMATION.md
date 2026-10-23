# Adding transactions from another app

Automation apps such as Tasker can open **New transaction** already filled in. Nothing is saved by the request itself. The screen opens with the values in place, you check them, and you tap **Save changes**, the check mark in the toolbar, the same as for a transaction you typed. With the lock on, the lock screen comes first.

## The action

Send an intent with this action to the activity, with the package set:

```
io.github.herrerad85.tallybook.action.ADD_TRANSACTION
```

The package is `io.github.herrerad85.tallybook`. The extras below fill in a new plain transaction only, never a transfer, a debt or a saving.

## The extras

Every extra is optional, and the keys are lowercase.

- `wallet` is the name of a wallet, matched without regard to case. Archived wallets are left out. When no wallet or more than one wallet carries that name, the screen opens on the wallet a new transaction usually starts in.
- `amount` is a number written with a dot for decimals, such as `12.50` or `1,234.56`. One currency symbol before or after it is fine. The sign is dropped, because the category decides whether money comes in or goes out. The amount is rounded to the decimals of the chosen wallet's currency. An amount the app cannot read, including one with a decimal comma such as `12,50`, leaves the amount at zero for you to type in.
- `category` is the name of an income or expense category, matched without regard to case. A subcategory counts too. System categories are left out. When no category or more than one category carries that name, the field stays empty.
- `description` is the text for the description, used as sent.
- `note` is the text for the note, used as sent.

There is no date extra. The transaction is dated the moment the screen opens, and you can change the date before saving.

When the request carries a description and no category, the rules under **Settings, Utilities, Automatic categories** are checked once as the screen opens, so a matching rule's category is already in place before you save.

## A Tasker example

In a Tasker task, add a **Send Intent** action and fill it in like this:

- Action: `io.github.herrerad85.tallybook.action.ADD_TRANSACTION`
- Cat: Default
- Package: `io.github.herrerad85.tallybook`
- Extra: `wallet:Cash`
- Extra: `amount:12.50`
- Extra: `description:Coffee`
- Target: Activity

Class can stay empty. Running the task opens **New transaction** on the Cash wallet with 12.50 and the description Coffee, waiting for you to pick a category, if no rule picked one, and save.

## Send large amounts as a string

Tasker sends a value that looks like a decimal number as a Float, and a Float keeps only about seven significant digits. A number with more digits than that loses precision before it reaches the app. For example `1234567.89` arrives as `1234567.9`. Send such an amount as a string. A value Tasker cannot read as a number, such as `1,234,567.89` with its thousands separators, already goes as a string.

## Saving without a tap

An automation app can also save a transaction straight away, with no screen to check first. This is off until you turn it on.

### The switch and the code

Open **Settings, Utilities** and, under **Automation**, turn on **Let automation apps save without a tap**. The app makes a new secret code and shows it. Tap **Copy** to put the code on the clipboard, then paste it into your automation app. Tap **New code** to replace it, for example after you shared the old one by mistake. The old code stops working at once. While the switch is on, **Show the code** under it opens the same window again.

Turning the switch off deletes the code. Turning it on again makes a new one, so every automation app needs the new code pasted in. Treat the code like a password. Any app on this phone that has it can add transactions while the switch is on. The code stays on this phone and is not part of any backup.

### The action

Send a broadcast with this action, with the package set:

```
io.github.herrerad85.tallybook.action.SAVE_TRANSACTION
```

The package is `io.github.herrerad85.tallybook`. Leave the category of the broadcast empty or set it to the default category. Any other category does not reach the app.

### The extras

The request takes the same five extras as above, `wallet`, `amount`, `category`, `description` and `note`, plus one more:

- `code` is the code from the settings, exactly as shown.

The app checks the request in this order:

1. The switch is on.
2. `code` matches the stored code.
3. There is a wallet. That is the wallet named in `wallet`, or the wallet a new transaction usually starts in when no wallet or more than one wallet carries that name.
4. `amount` reads as a number above zero once rounded to that wallet's currency. An amount the app cannot read, or one missing from the request, counts as zero.
5. There is a category. That is the category named in `category`. When no category or more than one category carries that name, or the extra is missing, it is the category a rule under **Automatic categories** picks for the description. With neither, there is no category.

A request that fails any check saves nothing and sends nothing back to the app that sent it. The check that failed is written to the system log, without the code and without any value from the request.

### What gets saved

A request that passes every check saves one plain transaction, dated the moment the request arrives, with the wallet, amount, category, description and note above. The category decides whether the money comes in or goes out, the same as in the editor.

The transaction arrives unconfirmed and shows the clock marker in the list. It counts toward balances and budgets only once you confirm it, by opening it, checking **Confirmed** and saving.

### A Tasker example

In a Tasker task, add a **Send Intent** action and fill it in like this:

- Action: `io.github.herrerad85.tallybook.action.SAVE_TRANSACTION`
- Cat: None
- Package: `io.github.herrerad85.tallybook`
- Extra: `code:<the code>`
- Extra: `amount:4.20`
- Extra: `category:Food`
- Target: Broadcast Receiver

Put the code you copied from the settings in place of `<the code>`. Running the task saves an unconfirmed 4.20 under Food in the wallet a new transaction usually starts in, with no screen opening.

### The app lock and a force stop

The app lock is not asked for. Nothing goes back to the app that sent the request, so the lock still hides everything in the app.

After a force stop, Android delivers nothing to the app until you open it once. A request sent in that time is lost.
