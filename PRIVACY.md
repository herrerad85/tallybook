# Privacy Policy

Tallybook is an offline first expense manager. Your records live on your device, this project runs no server, and the app collects nothing about you. Android's own backup can take a copy of the database off the device, which is described below.

This policy covers the open source build, the `floss` and `osm` flavor combination in this repository, which is what F-Droid and the GitHub releases both ship.

Last updated: August 20, 2026.

## What Tallybook collects

Nothing. There is no account, no sign up, no analytics, no crash reporting, and no advertising. The build carries no library that reports usage or errors anywhere.

## Every way your data leaves the app

**Map tiles.** Every screen with a map draws tiles from the OpenStreetMap tile servers by default, and nothing has to be turned on first. The tiles fetched are the ones around whatever place is on screen, so the tile operator sees your IP address and roughly where that place is, and the requests carry the app's package name as their user agent. The app asks for no location permission, so the place on screen is always one you picked, never where you are. Since version 1.3.0 you can point the map at a different tile server under Settings, Utilities, Map, Tile server. The address has to be https, and if your provider wants a key inside the address, that key goes out with every tile request. What the OpenStreetMap servers log is covered by their own policy: https://osmfoundation.org/wiki/Privacy_Policy

**Backup to your own WebDAV server.** The app uploads and downloads backup files at the address you gave it, which also has to be https, so the upload and the password travel encrypted. With automatic backup on, that upload runs on a schedule with nobody present, survives a reboot, and sends your whole database and every attachment each time. That traffic never passes through this project.

**Exchange rates.** Downloading exchange rates uses openexchangerates.org. No key ships with the app, so the feature does nothing until you register there and enter your own under Settings, Utilities, Exchange rates, Custom api-key. The key travels inside the address of the request, so it lands in their logs.

**Android's own cloud backup.** The app is declared eligible for Android's backup, and a rules file inside the app decides what the system may take. A cloud backup takes your database. Your settings stay out, and so do the WebDAV address, username and password, the automatic backup passwords, the exchange rate key, the tile server address and the PIN or pattern you set, all of which live in files the rules leave out. Whether it runs at all is a system setting, not anything this app asks you about. The rules also ask for a transport that reports it can encrypt what it takes, and on Google's that is what a screen lock gives you; a transport that reports nothing gets nothing from this app. On Android 12 and up a direct phone to phone transfer during setup carries your attachments as well, because it hands them to your next device instead of to a server, while a cloud backup leaves them behind; below Android 12 there is no way to tell those two apart, so neither carries attachments. Tested on Android 16 against Google's backup, on a build whose rules differed only in also offering attachments: the database is taken, no settings file is, and restoring brings the database back. Older Android versions were not tested.

**Other apps on your phone.** Several buttons hand something to whatever app the device has for it, with no network request from Tallybook: Open on a saved place passes coordinates to a maps app, usually Google Maps; opening an attachment passes that file to whatever opens its file type; and the chooser offered after an export passes the exported file the same way. Once a file is in another app, that app's own policy governs it.

## What is stored on your device

Your records are in a SQLite database in the app's private storage. Attachments are separate files, under `Android/data/io.github.herrerad85.tallybook` on shared storage, which is outside that private storage. In the same shared storage the map caches the tiles it has drawn, which leaves a record of where your places are. Every export also leaves a copy of itself in the app's cache, and every import leaves a copy of the file you imported. The app deletes neither.

The database, the attachments, the tile cache and those cached copies are not encrypted at rest. Neither are the secrets the app keeps in its settings: the WebDAV username and password, the exchange rate key, the tile server address if you put a key in it, and the PIN or pattern you set, which is stored exactly as you typed it. Anyone who can read the app's storage, which means root or an unlocked bootloader, can read the database and every one of those secrets, and the WebDAV password is the one to your own server.

You can lock the app with a PIN, a pattern, or your fingerprint. That locks the screen, not the files.

## Backups and exports

A backup is a zip file holding your database and your attachments, and you choose the destination: External Memory, a Local folder you pick, or your own WebDAV server. There is no backup password by default. Set one and the contents are encrypted with AES 256. Leave it empty and the zip carries your database and attachments in the clear.

Exports to CSV, XLS and PDF are never encrypted.

## Children

Tallybook collects nothing from anyone, which includes children.

## Changes to this policy

Changes are commits in this repository, so the history is public and dated.

## Contact

Open an issue: https://github.com/herrerad85/tallybook/issues
