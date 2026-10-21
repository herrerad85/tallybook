# Migrating from MoneyWallet to Tallybook

Tallybook is a **separate app** from the original MoneyWallet. It uses a different application id (`io.github.herrerad85.tallybook`), which means:

- Both apps can be installed at the same time, side by side.
- Installing Tallybook does **not** update or replace MoneyWallet, and it does **not** read MoneyWallet's data automatically.
- Tallybook starts empty. To bring your data across, migrate it manually using the steps below.

## Migration path: Backup and Restore

This path is verified. A backup made by the original MoneyWallet restores into Tallybook with full data fidelity, because Tallybook preserves the legacy backup format. The flow brings across your wallets, transactions, categories, budgets, and the rest:

1. In the original **MoneyWallet**: open Settings and create a local **Backup**. If you set a backup password, remember it, you will need it to restore.
2. Find the backup file in the folder you saved it to. Its name starts with `backup_` and the date, and it ends in `.mwbx`, or `.mwbs` if it has a password. Copy it to your computer to keep it safe, then use your phone's file manager to move it into a folder of its own, such as `Download/mwbackup/`. Recent Android versions won't let Tallybook open the `Download` folder itself, only a folder inside it.
3. Install **Tallybook** (it installs alongside MoneyWallet, it does not overwrite it).
4. In **Tallybook**, tap **Restore backup** on the first screen. If you already set Tallybook up, go to **Settings**, **Database**, **Backup services** instead. Pick **Local folder**, choose the folder that holds your backup, then tap the backup file and enter the password if one was set.
5. Check that your wallets and recent transactions appear, then carry on in Tallybook.

Keep MoneyWallet installed and your backup file safe until you are confident everything came across. This process does not delete anything from MoneyWallet.

## Don't have a MoneyWallet backup yet?

The path above relies on a backup file created by MoneyWallet. The primary migration path is unchanged: create a backup in MoneyWallet, then restore that `.mwbs` or `.mwbx` file in Tallybook. How you get that backup depends on whether MoneyWallet is still on your device.

**If MoneyWallet is still installed:** open MoneyWallet and create a backup first, before you install Tallybook or start relying on it. Keep that file somewhere safe (see the steps above).

**If MoneyWallet is no longer installed:** reinstall it from the same source you originally used, then open it and create a backup if your old data appears. The same source matters because Android only lets an app read existing app data when the new install is signed with the same key as the original. A copy from a different source, or a locally built APK, has a different signature and cannot open the old data.

- Installed from **F-Droid**? MoneyWallet is still available there: https://f-droid.org/packages/com.oriondev.moneywallet/
- Installed from **Google Play or another source**? You need the app from that same source, signed with the same key. A locally built APK will not have a matching signature.

Reinstalling MoneyWallet does **not** recover data that Android already deleted when the app was uninstalled. If MoneyWallet opens empty after you reinstall it, the old app data is no longer on the device. In that case you need an existing backup file, or an Android or device backup that includes the app's data, to restore from.

## Verification

End-to-end restore was verified on 2026-06-29. A backup produced by the genuine MoneyWallet from F-Droid (version 4.0.5.10, versionCode 75), running on Android 15, restored into Tallybook with full data fidelity: wallets, transactions, categories, amounts, timestamps, and balances all came across, and the data persisted across an app relaunch. Both an unprotected backup and a password-protected, AES-encrypted backup were restored successfully.

## Notes and current limitations

- **Backup/Restore is the recommended path** for a complete copy of your data. CSV, Excel, and PDF export are meant for use in other tools, not as a full round-trip migration.
- **On Android 15, restore through the Local folder option.** Under scoped storage, the legacy "External Memory" file browser may not list a backup that another app wrote (for example a MoneyWallet `.mwbx` in `Download`). If you do not see your file, use the Local folder option in Tallybook and keep the backup in a normal, non-restricted folder such as `Download/mwbackup/`. Broader export/import work under scoped storage is tracked in the project issues; if a backup is hard to locate or restore, please open an issue and include your Android version.
- Coming from a very old (pre-4.0) MoneyWallet? The app also includes a legacy importer for the old database; prefer Backup/Restore where possible and use the legacy import only as a fallback.

## Why Tallybook is a separate app

The original application id belongs to the upstream MoneyWallet project and cannot be reused for a distributed fork, so Tallybook ships under its own id. This keeps the two apps independent and lets you run them side by side while you migrate.
