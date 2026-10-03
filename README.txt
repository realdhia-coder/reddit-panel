Reddit Panel - Android app (WebView + your userscript)

BUILD THE APK (no Android Studio needed):
1. Create a free GitHub account and a new empty repository.
2. Upload ALL files of this folder to it (keep the folder structure, including the hidden .github folder).
3. Open the repository's "Actions" tab -> "Build APK" -> wait ~3-5 minutes.
4. Open the finished run, download the artifact "RedditPanel-apk", unzip it, and install app-debug.apk on your phone
   (allow "install unknown apps" when Android asks).

Or open this folder in Android Studio and press Run.

Edit app/src/main/assets/panel.js to change the script; it is the same script as the Tampermonkey version.
