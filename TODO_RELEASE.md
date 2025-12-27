# Release To-Dos

## Signing Configuration
- [ ] Generate a keystore file using Android Studio (Build > Generate Signed Bundle / APK) or keytool.
- [ ] Add the following properties to your `gradle.properties` (or `~/.gradle/gradle.properties`):
    ```properties
    RELEASE_STORE_FILE=/path/to/your/keystore.jks
    RELEASE_STORE_PASSWORD=your_store_password
    RELEASE_KEY_ALIAS=your_key_alias
    RELEASE_KEY_PASSWORD=your_key_password
    ```

## Build
- [ ] Run `./gradlew assembleRelease` to build the signed APK.
- [ ] Verify the APK is located at `app/build/outputs/apk/release/app-release.apk`.

## Testing
- [ ] Install the release APK on a physical device: `adb install app/build/outputs/apk/release/app-release.apk`
- [ ] Verify Google Sign-In works (Release SHA-1 fingerprint must be added to Firebase Console).
- [ ] Verify Firestore access works.

## Deployment
- [ ] Upload to Google Play Console (if applicable).
