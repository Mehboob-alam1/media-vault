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

Shelf does not have its own server. The first launch asks for your Firebase project. After that, opening the app starts a background upload and the screen stays empty.

## Firebase setup

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.shelf.archive`.
3. Install Shelf on a phone (or emulator) and copy the **SHA-1** from the setup screen into that Android app, if the console asks for a certificate.
4. Enable **Authentication → Sign-in method → Anonymous**.
5. Create a **Realtime Database** and a **Storage** bucket.
6. Publish the rules in `firebase/database.rules.json` and `firebase/storage.rules`. The same text is on the Firebase tab inside the app, with copy buttons.
7. Download `google-services.json` and import it in the app, or paste the project id, Android app id, API key, storage bucket, and database URL yourself.

The database URL is on the Realtime Database page. Newer projects use a regional host such as `https://PROJECT-default-rtdb.REGION.firebasedatabase.app`. Older ones use `https://PROJECT-default-rtdb.firebaseio.com`. If `google-services.json` has no `firebase_url`, paste the URL from the console. A guessed URL only works for the default US host.

Every phone that connects to the same Firebase project shares one library. Shelf signs in anonymously so Storage and Database rules can require `auth != null`.

In the console, open **Realtime Database → Data → library → files**. Each child has a `downloadUrl`. The bytes live under **Storage → library**.

A download URL works for anyone who has the link. Do not publish this app with a wide-open API key if the files are private.

## What happens when you open the app

The screen is empty. A low-priority notification, **Saving files**, is the only sign that work is running. Android requires that notification so it does not stop the upload.

On the first open, Shelf asks for:

1. Photos (or storage on Android 12 and older).
2. Notifications, on Android 13 and newer, so the progress line can appear.
3. All-files access, on Android 11 and newer. That is what lets Shelf read PDFs, Office files, and WhatsApp pictures stored outside the gallery.

After those prompts, Shelf scans shared storage and uploads images, PDFs, Word, Excel, PowerPoint, OpenDocument, and text files. Files already uploaded are skipped. Opening the app again scans for new or changed files. Files over 100 MB, and types such as zip archives, are skipped.

Press and hold the empty screen to open Firebase settings again. Back leaves that screen and returns to the blank background.

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
