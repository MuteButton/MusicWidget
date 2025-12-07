# Release Signing Configuration

## Creating a Release Keystore

To properly sign your release APK, you need to create a keystore file:

```bash
keytool -genkey -v -keystore music-widget-release.keystore -alias music-widget -keyalg RSA -keysize 2048 -validity 10000
```

**Important:** Keep this keystore file and password secure! Store it in a safe location (NOT in the git repository).

## Option 1: Configure Signing in build.gradle

Add this to your `app/build.gradle` file (above the `android` block):

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    // ... existing config ...

    signingConfigs {
        release {
            if (keystorePropertiesFile.exists()) {
                storeFile file(keystoreProperties['storeFile'])
                storePassword keystoreProperties['storePassword']
                keyAlias keystoreProperties['keyAlias']
                keyPassword keystoreProperties['keyPassword']
            }
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            // ... rest of config ...
        }
    }
}
```

Then create a `keystore.properties` file in your project root:

```properties
storeFile=/path/to/your/music-widget-release.keystore
storePassword=your_store_password
keyAlias=music-widget
keyPassword=your_key_password
```

**Add `keystore.properties` to `.gitignore`!**

## Option 2: Sign via Command Line

Build unsigned release APK:

```bash
./gradlew assembleRelease
```

Sign manually:

```bash
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore music-widget-release.keystore app/build/outputs/apk/release/app-release-unsigned.apk music-widget
```

Zipalign:

```bash
zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release-signed.apk
```

## Option 3: Sign via Android Studio

1. Go to **Build** → **Generate Signed Bundle / APK**
2. Select **APK**
3. Choose existing keystore or create new one
4. Fill in keystore details
5. Select **release** build variant
6. Click **Finish**

## For GitHub Actions / CI

Store keystore as base64:

```bash
base64 music-widget-release.keystore > keystore.base64
```

Add secrets to GitHub repository:

- `KEYSTORE_BASE64`: content of keystore.base64
- `KEYSTORE_PASSWORD`: your keystore password
- `KEY_ALIAS`: music-widget
- `KEY_PASSWORD`: your key password

## Quick Test Build (Debug Signing)

For local testing only, you can temporarily use debug signing:

In `app/build.gradle`, inside the `release` buildType:

```gradle
signingConfig signingConfigs.debug  // REMOVE BEFORE PRODUCTION!
```

This allows building and testing the release variant locally, but **must be removed** before:

- Publishing to Google Play Store
- Distributing to users
- Committing to production branch
