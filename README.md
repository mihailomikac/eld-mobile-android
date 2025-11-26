# ELD Driver - Android App

Android mobilna aplikacija za ELD sistem pisana u **Kotlin** sa **Jetpack Compose**.

## 🚀 Kako pokrenuti:

### 1. **Instalacija Android Studio**
- Preuzmi i instaliraj [Android Studio](https://developer.android.com/studio) (najnovija verzija)
- Tokom instalacije, instaliraj:
  - Android SDK
  - Android SDK Platform
  - Android Virtual Device (Emulator)

### 2. **Otvori projekat**
```bash
# U Android Studio-u:
File → Open → Selektuj folder: ELDDriverAndroid
```

### 3. **Sinhronizuj Gradle**
- Android Studio će automatski detektovati projekat
- Klikni na "Sync Now" kada se pojavi notifikacija
- Sačekaj da se preuzmu sve dependencies (prvi put može potrajati 5-10min)

### 4. **Podesi Emulator ili Device**
**Opcija A - Android Emulator:**
- Tools → Device Manager
- Klikni "Create Device"
- Izaberi Pixel 7 ili bilo koji noviji uređaj
- Sistem image: API 34 (Android 14)
- Klikni Finish

**Opcija B - Fizički telefon:**
- Omogući Developer Options na telefonu:
  - Settings → About Phone → Tap "Build Number" 7 puta
- Omogući USB Debugging:
  - Settings → Developer Options → USB Debugging
- Povežu telefon USB kablom

### 5. **Pokreni aplikaciju**
```bash
# U Android Studio:
1. Selektuj uređaj/emulator iz dropdown-a (gore desno)
2. Klikni zeleni "Run" dugme (▶️) ili pritisni Shift+F10
```

## 📱 Alternativno - Gradle komandom:

```bash
cd /Users/mihailotrisovic/Desktop/Projects/ELD/ELDDriverAndroid

# Build projekat
./gradlew build

# Instaliraj na povezan uređaj
./gradlew installDebug

# Pokreni app
adb shell am start -n com.eld.driver/.MainActivity
```

## 🛠 Struktura projekta:

```
ELDDriverAndroid/
├── app/
│   ├── src/main/
│   │   ├── java/com/eld/driver/
│   │   │   ├── MainActivity.kt          # Entry point
│   │   │   ├── ELDApp.kt               # Navigation setup
│   │   │   ├── ui/
│   │   │   │   ├── theme/              # Colors, Typography, Theme
│   │   │   │   ├── screens/            # All screens
│   │   │   │   └── components/         # Reusable components
│   │   │   └── data/
│   │   │       ├── models/             # Data models
│   │   │       └── api/                # API & Mock data
│   │   ├── res/                        # Resources (strings, icons)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## ✅ Trenutno implementirano:

- ✅ Projekat struktura
- ✅ Theme sistem (colors, typography, spacing)
- ✅ Svi data modeli (User, Vehicle, HOSStatus, DailyLog, DutyStatus)
- ✅ MockDataService za development
- ✅ MainActivity i ELDApp sa navigation setup-om
- ✅ Dashboard screen (referentna implementacija)

## 🔜 TODO - Preostali screens:

- ⏳ LoginScreen
- ⏳ VehicleConfirmationScreen
- ⏳ VehicleSelectionScreen
- ⏳ LogsScreen
- ⏳ LogDetailScreen
- ⏳ DVIRScreen
- ⏳ ChangeDutyStatusModal
- ⏳ SideMenuDrawer
- ⏳ Trailers Modal
- ⏳ Circular HOS Timer komponenta

## 📝 Napomene:

- **Mock data**: Trenutno koristi `MockDataService` umesto pravog API-ja
- **Backend**: Kada backend bude ready, postaviti `useMockData = false` u `MockDataService`
- **iOS paritet**: Sve funkcionalnosti su identične iOS verziji
- **Material Design 3**: Koristi najnoviji Material Design sistem

## 🔧 Troubleshooting:

**Problem**: "SDK location not found"
- File → Project Structure → SDK Location → Podesi Android SDK path

**Problem**: "Sync failed"
- File → Invalidate Caches → Invalidate and Restart

**Problem**: Slow build
- Povećaj heap size u `gradle.properties`: `org.gradle.jvmargs=-Xmx4096m`

**Problem**: Emulator je spor
- Omogući Hardware Acceleration (HAXM na Intel, Hypervisor na ARM Mac)
