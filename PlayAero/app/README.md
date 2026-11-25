# PlayerAero 🫧
Музыкальный плеер в стиле Frutiger Aero (Kotlin + Jetpack Compose + Media3).

## Как получить APK
**Вариант 1 — Android Studio:** открой папку проекта → дождись Sync → Build → Build APK(s).
Файл: app/build/outputs/apk/release/app-release.apk (подписан debug-ключом, ставится сразу).

**Вариант 2 — без установки чего-либо (GitHub Actions):** залей проект в репозиторий GitHub (ветка main) →
вкладка Actions → Build APK → скачай артефакт PlayerAero-apk.

**Вариант 3 — консоль:** `gradle assembleRelease` (нужны JDK 17, Android SDK 34, Gradle 8.7).
