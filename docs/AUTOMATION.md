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
