# The CSV import format

The header row carries the raw column keys, and a file written by hand needs the same ones. Five columns are required on every row: `wallet`, `currency`, `category`, `datetime` and `money`. Five more are optional: `description`, `event`, `people`, `place` and `note`.

`datetime` is `yyyy-MM-dd HH:mm:ss`, or `yyyy-MM-dd` for a row with no time of day. `currency` is the ISO code, and it has to be one the app already has. `money` is negative for an expense, and zero or above for income.

```
"wallet","currency","category","datetime","money","description"
"Everyday","USD","Groceries","2026-08-12 09:30:00","-12.34","market"
"Everyday","USD","Salary","2026-08-12","2000.00","august"
```

A row the importer will not read ends the import before anything from the file is saved, and the message names the line it stopped on. A row the CSV reader itself will not read, such as one with the wrong number of fields, ends it the same way but with the reader's own wording.

## Importing a file from another app or a bank

A file whose header does not name all five required columns, or that this app's own import cannot read, is read through a mapping screen instead. The import screen shows it after the file is picked. The separator is worked out from the header line, and a comma, a semicolon and a tab are all understood.

Pick the wallet every row goes into, and the column that holds the date and the amount. The description, note and category columns are optional. A row whose category cell is empty, or a file with no category column picked, puts the row under a category named "Imported", one for income and one for expenses.

The date format is one of `yyyy-MM-dd`, `dd/MM/yyyy`, `dd/MM/yy`, `MM/dd/yyyy`, `MM/dd/yy`, `dd.MM.yyyy` and `dd.MM.yy`. Each can be followed by a space and a time as `HH:mm` or `HH:mm:ss`. The year has exactly as many digits as the chosen format asks for, and a date that does not exist, such as February 30, is refused. A year written with two digits is placed in the century that puts it no more than 80 years before today and no more than 20 years after, so `26` is 2026 and `99` is 1999.

The decimal separator is either a dot, as in `1,234.56`, or a comma, as in `1.234,56`. Grouping is only accepted in threes, so `12,50` is refused when a dot is chosen, and `1,234.56` is refused when a comma is chosen. Nothing guesses which one was meant. One currency symbol is allowed before or after the number, with at most one space between them, as in `$1,234.56` or `1.234,56 €`. A letter code such as `USD` is refused. An amount written in parentheses, as in `(12.50)`, or with a minus at the end, as in `12.50-`, is negative, and such an amount cannot carry a sign of its own as well.

An amount below zero is an expense. When a file writes spending as a positive number, tick "Spending is written as a positive number" and every amount has its sign flipped.

The choices are remembered for the next file with the same header, all of them except the wallet. A wallet's id can change when a backup is restored, so it is picked again each time.

A row is skipped when its wallet already holds a transaction with the same date, amount and description, both income or both expenses, so importing the same file again saves nothing new. Two identical rows in one file are both saved the first time.
