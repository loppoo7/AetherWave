`gradle-wrapper.properties` pins Gradle 9.5.0. This archive intentionally omits `gradle-wrapper.jar`.

The GitHub Actions workflow installs Gradle 9.5.0 and runs `gradle wrapper --gradle-version 9.5.0` before invoking `./gradlew assembleDebug`. For local command-line builds, run the same wrapper-generation command once with an installed Gradle 9.5.0 distribution.
