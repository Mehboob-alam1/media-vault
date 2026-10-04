# Shelf

Shelf is an Android app that copies files off the phone into **your** Firebase project.

It uploads:

- gallery photos
- WhatsApp and WhatsApp Business images
- PDFs
- Word, Excel, PowerPoint, and OpenDocument files
- plain text (`.txt`, `.md`, `.csv`, `.log`)

Each file goes to **Cloud Storage**. Shelf then writes a record to **Realtime Database** at `library/files/{id}` with the file name, type, size, and `downloadUrl`. Open the Firebase console to see those links. The phone screen stays blank.

Phones and tablets from Android 7.0 (API 24) through current Android releases can install it.

Shelf does not have its own server. Firebase is read from `app/google-services.json`, which is packaged into the app. There is no setup screen.

## Firebase

`app/google-services.json` already points at the Firebase project. The Android package inside that file for this app is `com.shelf.archive`. In Authentication, Anonymous must be enabled, and under Settings **Enable create (sign-up)** must be on. Realtime Database and Storage stay enabled, with the rules in `firebase/database.rules.json` and `firebase/storage.rules` published.

Every phone that installs this app shares one library. Shelf signs in anonymously so Storage and Database rules can require `auth != null`.

In the console, open **Realtime Database → Data → library → files**. Each child has a `downloadUrl`. The bytes live under **Storage → library**.

A download URL works for anyone who has the link. Do not publish this app with a wide-open API key if the files are private.

## What happens when you open the app

The screen is empty. A low-priority notification, **Saving files**, is the only sign that work is running. Android requires that notification so it does not stop the upload.

On the first open, Shelf asks for:

1. Photos (or storage on Android 12 and older).
2. Notifications, on Android 13 and newer, so the progress line can appear.
3. All-files access, on Android 11 and newer. That is what lets Shelf read PDFs, Office files, and WhatsApp pictures stored outside the gallery.

After those prompts, Shelf scans shared storage. It uploads PDFs, Office files, and text first, with WhatsApp and WhatsApp Business documents ahead of the others. Photos go up after those documents. Files already uploaded are skipped. Opening the app again scans for new or changed files. Files over 100 MB, and types such as zip archives, are skipped.

## Run it

Open this folder in Android Studio (Quail or newer, Android Gradle Plugin 9.4). Create an emulator or plug in a phone, then run the `app` configuration.

From the command line, with the Android SDK installed:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever your SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. `minSdk` is 24, `targetSdk` is 36, and the project compiles against API 37.

`local.properties` is not committed. Point `sdk.dir` at your SDK before building.
