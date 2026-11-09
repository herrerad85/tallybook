# Contributing to Tallybook

Bug reports and pull requests are welcome in the [issue tracker](https://github.com/herrerad85/tallybook/issues). Current direction and open work live in the pinned [roadmap issue](https://github.com/herrerad85/tallybook/issues/15).

People keep years of their own financial records in this app, and it runs on phones going back to Android 7. A change that works once, on one modern phone, in portrait, has not been tested yet.

## Start with an issue

Every pull request links an issue, and for a bug the issue comes first. Translations are the exception.

- **Find it by using the app.** A bug report starts from what someone does and sees, like "I open a budget, rotate the phone, and the amount resets." A problem found only by reading the code is a guess until you can make it happen in the app.
- **Search before you file.** If the problem is already reported, add to that issue instead of opening a second one.
- **The issue carries the steps, not the fix.** Say what you did, what you expected and what happened. The fix and how it works go in the pull request.
- An issue written after the fix, to justify it, does not count.

## Using AI to write a change

AI written pull requests are welcome here. There is nothing to hide and nothing to announce. The bar is the same as for any other patch, and the work of getting a change over that bar is yours, not the model's.

- Read every line you are about to submit and be able to explain why it is there. If you cannot explain a line, delete it and find out what it was for.
- A model states a cause with the same confidence whether it traced one or not. Trace the mechanism yourself before you write it into a pull request body, a commit message or a code comment. A commit message is a claim, and a wrong one sends the next person down the wrong path.
- Watch for the things models invent: a file path that is not in the tree, a method nobody calls, an API that does not exist on API 24, a test that passes because it asserts the code's current behavior instead of the correct one.
- "It should be a small fix" is a prediction, not a finding. When the change looks smallest, check it hardest.

## Writing the code

### Kotlin

- **New code is Kotlin.** New classes, new files and new tests are written in Kotlin. Do not add Java files.
- **A fix inside an existing Java file stays in Java**, and stays as small as the fix needs. The app is moving to Kotlin one package at a time, in pull requests that carry nothing else. Converting a file in the same pull request as a fix buries the few lines that matter under a diff of every line.
- **Kotlin still has to work where Java code and Android reach it.** A `Parcelable` keeps `@JvmField` on its `CREATOR`, because Android reads that field by reflection and needs it to be a public static field.

### Comments

- **No comments inside function bodies.** If a line needs a comment to be understood, rename the variable or pull the step into a function whose name says what it does.
- **Never comment a call or a variable.** Their names explain them.
- **A KDoc, or Javadoc in a Java file, only when a declaration's contract is not obvious** from its name and types, for example what a null return means or what a caller must do first. Describe what the code does, and do not argue that it is right.
- **No comment that restates the name.** "getWallet: gets the wallet" tells the reader nothing.
- **The why goes in the commit message.** The history, the workaround and what forced it, the crash a guard prevents. `git blame` takes the next person straight to it.
- Comments already in code you are not otherwise changing stay where they are. Removing them is an unrelated change.

### Keep pull requests small

- **One concern per pull request.** A fix, a feature or a cleanup, not two of them. Unrelated cleanup makes a change harder to review and harder to revert.
- **Split work that splits.** When a feature divides into steps that each build and pass the tests, send them as separate pull requests, in order.
- **Touch only the files the change needs.** Reformatting, renaming or tidying files you were not otherwise changing makes a small fix hard to find in the diff. New files are fine. A change that has to reach many files for a real reason, like removing a string from every translation, is fine too, and the body says why.
- **A refactor changes no behavior.** If anything a user can see changes, it is not a refactor, and the body says what changed.

### Fixing a bug

- **Fix it where every caller goes through.** A report names one path. Patching only that path leaves every sibling caller broken, and one guard in the shared function is a smaller change than a guard in each caller.
- **Read the history of a line before you change it.** `git blame` and `git log -L <line>,<line>:<path>` take seconds and name the commit and the issue that put the line there. Code that looks arbitrary, a guard, a disabled default, a magic number, usually has a crash behind it. Layouts and resource files carry decisions the same way source does.

### Structure

- **One function, one job.** A function that loads, checks, formats and saves is four functions.
- **Shared helpers go in a utility class** under `utils`, not at the bottom of the screen that first needed them.
- **No code nobody calls.** No getters, setters or overloads added in case they are useful later, and no second way to read the same setting.
- **Use the words the codebase already uses.** A name says what the thing does. Do not invent a term for your change, since nobody else will know to search for it.
- **Text a user sees goes in `strings.xml`**, never typed into code or a layout, or it cannot be translated. A label shown on screen is never also a stored value or a key, because renaming the label later would break saved data.
- **Keep the main thread free.** New database, file or network work does not go on the main thread or into app start.
- **No new dependency for what a few lines do.** The `floss` flavor ships no proprietary services and F-Droid builds it from source, so anything added has to be free software and resolvable in that build.
- **License.** Tallybook is GPLv3 or later. Code adapted from another project has to be compatible with that, which GPLv2 only is not, and the pull request needs to say where it came from.

## Cold read your own diff

Getting a change to work only answers the easy question. Before you open the pull request, read the complete diff again as a stranger would, someone with no memory of how the code got that way and no investment in the design being right. That is the cold read.

The question to ask is not whether the change is correct.

- **What did the change make newly possible?** A new nullable is a new crash path. A new early return skips cleanup that used to run. Work moved off the main thread opens a lifecycle race that could not exist before.
- **Does the codebase already do this?** Look for the existing helper before writing a second one a few files over.
- **Every caller, not just the one in the issue.** Grep the function you touched.

A second model that did not write the code finds things the one that wrote it cannot see, so give the diff to a fresh session with no history of the work and tell it to break the change. What it reports is a candidate, not an order. Verify each finding in the code yourself, because a confident wrong finding produces a fix for a defect that was never there. Fixes written in response to a cold read get their own cold read, and you stop when a pass finds nothing or changes nothing.

## Testing

Build and run the unit tests:

```
./gradlew clean testFlossOsmDebugUnitTest assembleFlossOsmDebug lintFlossOsmDebug
```

Pass `clean`, the way CI does. An up to date task is skipped, so a green test count can be one that predates your edit.

A new test should fail without your fix. Undo the fix, run the test and watch it fail, then put the fix back. A test that passes either way proves nothing.

The same command runs in CI on every pull request. The instrumented tests are compiled there but only run once a week, on one emulator at API 35, so a change touching the database or the UI needs you to run it on a device yourself.

Run the app, on an emulator or a phone, on both ends of the range:

- **API 24, Android 7.0.** This is `minSdk`, and it is a real floor, not a formality. Newer APIs, newer library behavior and newer syntax silently compile and then fail there.
- **A current Android.** The app targets API 35, where permissions, storage and edge to edge behavior all differ from the floor.

Rotation and theming have both broken screens in this app before.

- **Landscape.** Rotate every screen your change touches, including with a dialog open and with half typed text in a field. A rotation destroys and recreates the activity, so anything held only in memory is gone unless it was saved.
- **Dark theme**, and a couple of the accent themes if you changed anything visual.
- **The reported input.** Reproduce the original bug first, so you know it is real and you know the steps. Then run those same steps on your build. A passing unit test is not the same evidence as the bug being gone.

Screenshots help on anything visual, before and after. Use invented amounts and category names, never a real ledger.

## Lint

Lint runs in CI with `abortOnError true`, so an error fails the build. Warnings do not fail it, and warnings your change introduces are still yours to fix.

`app/lint-baseline.xml` records what was already there before a check was turned on. It is not permission to add more. Do not regenerate it to make a new warning disappear.

## The pull request

Write the body yourself, in plain words, for someone who has not seen the code.

- **Link the issue** it fixes.
- **Say what was broken and how you know it is fixed**, with steps someone else can run.
- **Give the argument.** When a reviewer would reasonably ask why it was done this way and not the obvious way, answer that. A list of what changed is not an argument, the diff already shows it.
- **Say how the pieces fit** when the change adds a class or moves work from one place to another.
- **Name what you did not test.** Every claim in the body should be one you checked. "I did not test X" is a fine sentence, and a confident wrong one is not.
- **Answer the review.** Reply to every question, and read the whole review before you push again, not only the newest comment.

## Checklist before you open it

- [ ] An issue exists, written from what a user does and sees, and the pull request links it.
- [ ] New code is Kotlin. A fix in a Java file stays Java and stays small.
- [ ] No comments inside function bodies or on calls and variables. The why is in the commit message.
- [ ] One concern, and no file touched without a reason.
- [ ] One job per function, shared helpers in `utils`, nothing added that nobody calls.
- [ ] Names use the codebase's own words.
- [ ] Text a user sees is in `strings.xml`.
- [ ] Cold read done, and every caller of what you changed checked.
- [ ] Each new test fails without the fix.
- [ ] Run on API 24 and a current Android, rotated, in dark theme, with the steps from the issue.
- [ ] The Gradle command under Testing passes, with no new lint warnings.
- [ ] The body gives the argument and names what was not tested.

## Translating

Translating needs no tooling and no build. Copy `app/src/main/res/values/strings.xml` into a folder named for the language, such as `values-de`, translate the entries and open a pull request. Leave any entry you are unsure about in English, and check that longer translations do not clip the layout.
