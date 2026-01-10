# ELD Driver Android - Kompletna Tehnička Dokumentacija

> **Verzija:** 1.0
> **Datum:** Decembar 2024
> **Statistika:** 75 Kotlin fajlova | 22,148 linija koda

---

## Sadržaj

1. [Pregled Arhitekture](#1-pregled-arhitekture)
2. [Root Level Fajlovi](#2-root-level-fajlovi)
3. [BLE Modul - Bluetooth ELD Integracija](#3-ble-modul---bluetooth-eld-integracija)
4. [Data Modul - API, Modeli, Baza](#4-data-modul---api-modeli-baza)
5. [HOS Modul - Hours of Service](#5-hos-modul---hours-of-service)
6. [Location Modul - GPS i Lokacija](#6-location-modul---gps-i-lokacija)
7. [Sync Modul - Offline Sinhronizacija](#7-sync-modul---offline-sinhronizacija)
8. [UI Modul - Ekrani i Komponente](#8-ui-modul---ekrani-i-komponente)
9. [FMCSA Usklađenost](#9-fmcsa-usklađenost)
10. [Tehnički Stack](#10-tehnički-stack)

---

## 1. Pregled Arhitekture

### Arhitekturni Dijagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        ELD Driver Android                        │
├─────────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │  Login   │ │Dashboard │ │  Logs    │ │   DVIR   │            │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘            │
│       │            │            │            │                   │
│  ┌────▼────────────▼────────────▼────────────▼────┐             │
│  │              ViewModels (State Management)      │             │
│  └────────────────────┬───────────────────────────┘             │
├───────────────────────┼─────────────────────────────────────────┤
│  Domain Layer         │                                          │
│  ┌────────────────────▼───────────────────────────┐             │
│  │              ELDRepository                      │             │
│  │    (Single Source of Truth - Offline First)    │             │
│  └──────┬─────────────┬─────────────┬─────────────┘             │
│         │             │             │                            │
│  ┌──────▼──────┐ ┌────▼────┐ ┌──────▼──────┐                    │
│  │ HOSService  │ │SyncMgr  │ │LocationSvc  │                    │
│  │ Calculator  │ │         │ │FMCSAFormat  │                    │
│  │ Violations  │ │         │ │             │                    │
│  └─────────────┘ └─────────┘ └─────────────┘                    │
├─────────────────────────────────────────────────────────────────┤
│  Data Layer                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  ApiService │  │ Room DB     │  │  BLE Mgr    │              │
│  │  (Retrofit) │  │ (SQLite)    │  │ (Geometris) │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│         ▼                ▼                ▼                      │
│    ┌─────────┐    ┌───────────┐    ┌───────────┐                │
│    │ Backend │    │ Local DB  │    │ ELD Uređaj│                │
│    │   API   │    │ (Offline) │    │(Bluetooth)│                │
│    └─────────┘    └───────────┘    └───────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

### Ključni Principi

| Princip | Implementacija |
|---------|----------------|
| **Offline-First** | Svi podaci se prvo čuvaju lokalno, pa sinhronizuju |
| **Single Source of Truth** | Room baza je jedini izvor podataka za UI |
| **Reactive UI** | StateFlow/Flow za reaktivno ažuriranje |
| **FMCSA Compliant** | Puna usklađenost sa 49 CFR 395 |

---

## 2. Root Level Fajlovi

### 2.1 MainActivity.kt
```
Lokacija: /app/src/main/java/com/eld/driver/MainActivity.kt
Linije: ~30
```

**Svrha:** Entry point Android aplikacije

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ELDDriverTheme {
                ELDApp()  // Glavni composable
            }
        }
    }
}
```

### 2.2 ELDApp.kt
```
Lokacija: /app/src/main/java/com/eld/driver/ELDApp.kt
Linije: ~250
```

**Svrha:** Glavni navigation host i composition root

**Ključne Funkcionalnosti:**
- NavHost sa svim rutama
- Session restoration (auto-login)
- Shared ViewModels
- Auto-navigacija na Dashboard kada brzina > 5 mph

**Definisane Rute:**
```kotlin
NavHost(navController, startDestination = "login") {
    composable("login") { LoginScreen(...) }
    composable("vehicle_selection") { VehicleSelectionScreen(...) }
    composable("vehicle_confirmation") { VehicleConfirmationScreen(...) }
    composable("dashboard") { DashboardScreen(...) }
    composable("logs") { LogsScreen(...) }
    composable("log_detail/{date}") { LogDetailScreen(...) }
    composable("inspections") { InspectionListScreen(...) }
    composable("dvir") { DVIRScreen(...) }
    composable("ble_test") { BleTestScreen(...) }
}
```

### 2.3 ELDDriverApplication.kt
```
Lokacija: /app/src/main/java/com/eld/driver/ELDDriverApplication.kt
Linije: ~460
```

**Svrha:** Globalna Application klasa sa singleton menadžerima

**Odgovornosti:**
- Session management (token, user, vehicle ID)
- BLE device connection/disconnection
- Inicijalizacija core servisa
- Automatska promena duty statusa na osnovu telemetrije
- ELD connection tick event handling

**Ključne Metode:**
```kotlin
companion object {
    fun setAuthToken(token: String?)      // Čuvanje tokena
    fun getAuthToken(): String?           // Dobijanje tokena
    fun setCurrentVehicleId(vehicleId: Int?)
    fun getCurrentVehicleId(): Int?
    fun setCurrentUser(user: User?)
    fun getCurrentUser(): User?
    fun disconnectELD()                   // Disconnect od ELD-a
    fun clearSession()                    // Potpuni logout
    fun getBleManager(): GeometrisWQManager?
}
```

**Callback Sistem:**
```kotlin
// Auto-navigacija kada vozilo krene
var onNavigateToDashboard: (() -> Unit)? = null

// Callback za automatsku promenu statusa
bleManager.onAutoStatusChangeNeeded = { suggestedStatus, reason ->
    // Menja status na DRIVING ili ON_DUTY automatski
}

// ELD connection events
bleManager.onEldConnected = {
    // Šalje CONNECTED tick event
    // Briše power malfunction
}

bleManager.onEldDisconnected = {
    // Šalje DISCONNECTED tick event
    // Prijavljuje power malfunction
}
```

---

## 3. BLE Modul - Bluetooth ELD Integracija

```
Lokacija: /app/src/main/java/com/eld/driver/ble/
Fajlovi: 7
Linije: ~2,400
```

### 3.1 GeometrisWQManager.kt (~800 linija)

**Svrha:** Glavni ELD device manager koristeći Geometris wqlib SDK v1.0.10

**Arhitektura:**
```
┌─────────────────────────────────────────────────┐
│           GeometrisWQManager (Singleton)         │
├─────────────────────────────────────────────────┤
│  State Flows:                                    │
│  ├─ connectionState: BleConnectionState         │
│  ├─ eldData: GeometrisEldData?                  │
│  ├─ vehicleMotionState: IN_MOTION/STATIONARY    │
│  └─ unidentifiedEvents: List<UnidentifiedEvent> │
├─────────────────────────────────────────────────┤
│  Callbacks:                                      │
│  ├─ onAutoStatusChangeNeeded                    │
│  ├─ onEldConnected                              │
│  └─ onEldDisconnected                           │
├─────────────────────────────────────────────────┤
│  Methods:                                        │
│  ├─ startScan()                                 │
│  ├─ connect(device)                             │
│  ├─ disconnect()                                │
│  ├─ requestEldData()                            │
│  └─ startUnidentifiedEvents()                   │
└─────────────────────────────────────────────────┘
```

**Detekcija Kretanja:**
```kotlin
// Prag brzine za kretanje: 5 mph (8 km/h)
private const val SPEED_THRESHOLD_MPH = 5.0

// Idle timer: 5 minuta stajanja = prikaži dialog
private const val IDLE_TIMER_MS = 5 * 60 * 1000L

// Logika detekcije
when {
    speed >= SPEED_THRESHOLD_MPH && previousState == STATIONARY -> {
        // Vozilo je krenulo - predloži DRIVING
        onAutoStatusChangeNeeded?.invoke("DRIVING", "Vehicle started moving")
    }
    speed < SPEED_THRESHOLD_MPH && previousState == IN_MOTION -> {
        // Vozilo je stalo - pokreni idle timer
        startIdleTimer()
    }
}
```

**Auto-Reconnect Logika:**
```kotlin
// Exponential backoff: 2s, 4s, 8s, 16s, 32s (max 5 pokušaja)
private fun scheduleReconnect(attempt: Int) {
    val delayMs = (2.0.pow(attempt) * 1000).toLong()
    delay(delayMs)
    connect(lastDevice)
}
```

### 3.2 GeometrisDataParser.kt (~400 linija)

**Svrha:** Parsiranje BLE paketa od Geometris uređaja

**Podržani Protokoli:**
| Protokol | Firmware | Format |
|----------|----------|--------|
| V0 | Stariji | Fixed pozicije bajtova |
| V1 | Trenutni | Variable-length sa identifikatorima |

**Ekstraktovani Podaci:**
```kotlin
data class GeometrisEldData(
    val vin: String?,              // VIN vozila
    val serialNumber: String?,      // Serijski broj ELD-a
    val rpm: Int?,                  // Obrtaji motora
    val engineHours: Double?,       // Sati rada motora
    val speed: Double?,             // Brzina (km/h)
    val odometer: Double?,          // Kilometraža
    val latitude: Double?,          // GPS širina
    val longitude: Double?,         // GPS dužina
    val gpsTime: Long?,            // GPS timestamp
    val protocolVersion: Int        // Verzija protokola
)
```

**Multi-Packet Assembly:**
```kotlin
// BLE paketi dolaze u delovima od 20 bajtova
fun addPacket(packet: ByteArray): Boolean {
    buffer.addAll(packet.toList())
    return isComplete()  // true kada je ceo paket primljen
}
```

### 3.3 GeometrisBleService.kt (~150 linija)

**Svrha:** BLE service konstante i UUID-ovi

```kotlin
object GeometrisBleService {
    // Service UUID
    val SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    // Characteristic UUIDs
    val OBD_DATA_UUID = ...        // Za primanje podataka
    val OBD_CONTROL_UUID = ...     // Za slanje komandi
    val OBD_DEVICE_ADDRESS = ...   // Device address

    // Device name prefix
    const val DEVICE_NAME_PREFIX = "WQ-"

    // Komande
    const val CMD_GET_ELD_DATA = 0x01
    const val CMD_START_UD_EVENTS = 0x02
    const val CMD_PURGE_UD_EVENTS = 0x03
}
```

### 3.4 BLE Models

#### BleConnectionState.kt
```kotlin
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    data class DeviceFound(val device: BluetoothDevice, val rssi: Int)
    data class Connecting(val device: BluetoothDevice)
    data class Connected(val device: BluetoothDevice)
    data class ServicesDiscovered(val device: BluetoothDevice)
    data class Ready(val device: BluetoothDevice)  // Spreman za podatke
    data class Reconnecting(val device: BluetoothDevice, val attempt: Int, val maxAttempts: Int)
    data class Error(val message: String, val throwable: Throwable?)
}
```

#### UnidentifiedEvent.kt
```kotlin
data class UnidentifiedEvent(
    val reasonCode: Int,           // Razlog (END_STOP, BEGIN_STOP, etc.)
    val timestamp: Long,           // Unix timestamp (sekunde)
    val engineHours: Double,       // Sati motora
    val speed: Double,             // Brzina
    val odometer: Double,          // Kilometraža
    val latitude: Double,          // GPS širina
    val longitude: Double          // GPS dužina
) {
    val reasonDescription: String
        get() = when (reasonCode) {
            1 -> "End Stop"
            2 -> "Begin Stop"
            3 -> "BLE Disconnect"
            4 -> "BLE Connect"
            5 -> "Bus Malfunction"
            else -> "Unknown ($reasonCode)"
        }
}
```

---

## 4. Data Modul - API, Modeli, Baza

```
Lokacija: /app/src/main/java/com/eld/driver/data/
Fajlovi: 25+
Linije: ~4,000
```

### 4.1 API Layer

#### ApiService.kt (~200 linija)

**Svrha:** Retrofit API interface za sve backend endpoint-e

```kotlin
interface ApiService {
    // ═══════════════════════════════════════════════
    // AUTENTIFIKACIJA
    // ═══════════════════════════════════════════════
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ═══════════════════════════════════════════════
    // VOZILA
    // ═══════════════════════════════════════════════
    @GET("api/mobile/drivers/vehicles")
    suspend fun getVehicles(
        @Header("Authorization") token: String,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<ApiResponse<MobileVehicleListData>>

    // ═══════════════════════════════════════════════
    // DUTY STATUS
    // ═══════════════════════════════════════════════
    @GET("api/mobile/drivers/status")
    suspend fun getCurrentDutyStatus(
        @Header("Authorization") token: String
    ): Response<ApiResponse<DutyStatus>>

    @POST("api/mobile/drivers/status")
    suspend fun changeDutyStatus(
        @Header("Authorization") token: String,
        @Body request: DutyStatusChangeRequest
    ): Response<ApiResponse<DutyStatus>>

    // ═══════════════════════════════════════════════
    // TICK EVENTS (Connection/System events)
    // ═══════════════════════════════════════════════
    @POST("api/mobile/drivers/tick-events")
    suspend fun createTickEvent(
        @Header("Authorization") token: String,
        @Body request: TickEventRequest
    ): Response<ApiResponse<Any>>

    // ═══════════════════════════════════════════════
    // INTERMEDIATE EVENTS (FMCSA - svakih 60 min tokom vožnje)
    // ═══════════════════════════════════════════════
    @POST("api/mobile/drivers/intermediate-events")
    suspend fun createIntermediateEvent(
        @Header("Authorization") token: String,
        @Body request: IntermediateEventRequest
    ): Response<ApiResponse<IntermediateEventResponse>>

    // ═══════════════════════════════════════════════
    // DVIR INSPEKCIJE
    // ═══════════════════════════════════════════════
    @POST("api/mobile/drivers/inspections")
    suspend fun createInspection(...)

    @GET("api/mobile/drivers/inspections")
    suspend fun getInspections(...)

    // ═══════════════════════════════════════════════
    // LOGOVI I HOS
    // ═══════════════════════════════════════════════
    @GET("api/mobile/drivers/logs")
    suspend fun getDriverLogs(
        @Header("Authorization") token: String,
        @Query("days") days: Int = 7
    ): Response<ApiResponse<DriverLogsData>>

    @GET("api/mobile/drivers/events")
    suspend fun getDriverEvents(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null
    ): Response<ApiResponse<DriverEventsData>>

    @POST("api/mobile/drivers/hos")
    suspend fun updateHOSStatus(...)

    @POST("api/mobile/drivers/hos/violations")
    suspend fun createViolation(...)
}
```

### 4.2 Modeli

#### DutyStatusType.kt
```kotlin
enum class DutyStatusType {
    OFF_DUTY,              // Van dužnosti
    ON_DUTY_NOT_DRIVING,   // Na dužnosti, ne vozi
    SLEEPER_BERTH,         // Spavaonica
    DRIVING,               // Vožnja
    PERSONAL_CONVEYANCE,   // Lična vožnja
    YARD_MOVE              // Pomeranje u dvorištu
}
```

#### HOSStatus.kt
```kotlin
data class HOSStatus(
    // Break (30-min pauza)
    val breakRemaining: String = "00:00",
    val breakUsed: String = "00:00",
    val breakTotal: String = "08:00",

    // Drive (11h limit)
    val driveRemaining: String = "11:00",
    val driveUsed: String = "00:00",
    val driveTotal: String = "11:00",

    // Shift (14h limit)
    val shiftRemaining: String = "14:00",
    val shiftUsed: String = "00:00",
    val shiftTotal: String = "14:00",

    // Cycle (70h/8 dana)
    val cycleRemaining: String = "70:00",
    val cycleUsed: String = "00:00",
    val cycleTotal: String = "70:00",

    // Violations
    val hasViolation: Boolean = false,
    val violations: List<HosViolationDto> = emptyList()
) {
    // Progress za UI (0.0 - 1.0)
    val breakProgress: Float get() = ...
    val driveProgress: Float get() = ...
    val shiftProgress: Float get() = ...
    val cycleProgress: Float get() = ...
}
```

#### TickEventType.kt
```kotlin
enum class TickEventType {
    // Login/Logout
    LOGIN,
    LOGOUT,

    // ELD Connection
    CONNECTED,
    DISCONNECTED,

    // Power events
    POWER_UP,
    POWER_DOWN,
    ELD_UNPLUGGED,
    ELD_REPLUGGED,

    // Malfunctions (FMCSA 49 CFR 395.34)
    MALFUNCTION_POWER,
    MALFUNCTION_ENGINE_SYNC,
    MALFUNCTION_TIMING,
    MALFUNCTION_POSITIONING,
    MALFUNCTION_DATA_RECORDING,
    MALFUNCTION_DATA_TRANSFER,
    MALFUNCTION_CLEARED
}
```

### 4.3 Lokalna Baza (Room)

#### ELDDatabase.kt
```kotlin
@Database(
    entities = [
        DutyStatusEventEntity::class,
        TickEventEntity::class,
        HOSStatusEntity::class,
        SyncQueueEntity::class,
        USCityEntity::class,
        DailyLogEntity::class,
        ViolationRecordEntity::class
    ],
    version = 5
)
abstract class ELDDatabase : RoomDatabase() {
    abstract fun dutyStatusEventDao(): DutyStatusEventDao
    abstract fun tickEventDao(): TickEventDao
    abstract fun hosStatusDao(): HOSStatusDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun usCityDao(): USCityDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun violationRecordDao(): ViolationRecordDao

    companion object {
        @Volatile
        private var INSTANCE: ELDDatabase? = null

        fun getInstance(context: Context): ELDDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ELDDatabase::class.java,
                    "eld_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

#### DutyStatusEventEntity.kt
```kotlin
@Entity(tableName = "duty_status_events")
data class DutyStatusEventEntity(
    @PrimaryKey val id: String,
    val serverId: Int? = null,
    val dutyStatus: String,          // OFF_DUTY, DRIVING, etc.
    val startTime: Long,             // Unix ms
    val endTime: Long? = null,       // null = aktivan
    val durationMinutes: Int? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val vehicleId: Int? = null,
    val odometer: Double? = null,
    val engineHours: Double? = null,
    val note: String? = null,
    val isActive: Boolean = false,
    val isSynced: Boolean = false,
    val pendingSync: Boolean = true,
    val localCreatedAt: Long,
    val serverCreatedAt: Long? = null
)
```

#### TokenManager.kt (~300 linija)

**Svrha:** Sigurno čuvanje tokena i sesije

```kotlin
class TokenManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("eld_prefs", Context.MODE_PRIVATE)

    // Token management
    fun saveToken(token: String)
    fun getToken(): String?
    fun isTokenExpired(): Boolean

    // Vehicle management
    fun saveVehicleId(vehicleId: Int?)
    fun getVehicleId(): Int?

    // User management
    fun saveUser(user: User)
    fun getUser(): User?

    // Company timezone (iz JWT tokena)
    fun getCompanyTimeZone(): TimeZone
    fun getCompanyTimeOffset(): Int  // minuti

    // Driver settings (iz JWT DriverData claim-a)
    fun isYardMoveAllowed(): Boolean
    fun isPersonalConveyanceAllowed(): Boolean

    // Session
    fun isLoggedIn(): Boolean
    fun clear()

    // Recent emails (za brži login)
    fun addRecentEmail(email: String)
    fun getRecentEmails(): List<String>
}
```

### 4.4 Repository

#### ELDRepository.kt (~500 linija)

**Svrha:** Single source of truth - Offline-first pattern

```kotlin
class ELDRepository private constructor(context: Context) {
    private val apiService = ApiService.getInstance()
    private val database = ELDDatabase.getInstance(context)
    private val syncManager = SyncManager.getInstance(context)
    private val hosService = HOSService.getInstance(context)

    // ═══════════════════════════════════════════════
    // HOS STATUS
    // ═══════════════════════════════════════════════

    fun getHOSStatusFlow(): Flow<HOSStatus?> {
        return hosService.hosStatus.map { result ->
            result?.let { hosService.getHOSStatusModel() }
        }
    }

    suspend fun recalculateHOS() {
        hosService.recalculateNow()
    }

    // ═══════════════════════════════════════════════
    // DUTY STATUS
    // ═══════════════════════════════════════════════

    suspend fun changeDutyStatus(request: DutyStatusChangeRequest): Result<Unit> {
        return try {
            // 1. Sačuvaj lokalno ODMAH
            syncManager.createLocalDutyStatusEvent(request)

            // 2. Rekalkuliši HOS
            hosService.recalculateNow()

            // 3. Sync će se desiti automatski u pozadini
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════
    // TICK EVENTS
    // ═══════════════════════════════════════════════

    suspend fun createTickEvent(request: TickEventRequest) {
        syncManager.createLocalTickEvent(request)
    }

    // ═══════════════════════════════════════════════
    // LOGS
    // ═══════════════════════════════════════════════

    suspend fun getDriverLogs(token: String, days: Int): Result<List<DailyLogDto>> {
        // Vraća iz lokalne baze, ne sa servera
        val events = database.dutyStatusEventDao()
            .getEventsSince(System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L)
        return Result.success(groupEventsByDay(events))
    }
}
```

---

## 5. HOS Modul - Hours of Service

```
Lokacija: /app/src/main/java/com/eld/driver/hos/
Fajlovi: 5
Linije: ~2,000
```

### 5.1 HOSCalculator.kt (~500 linija)

**Svrha:** Kalkulacija HOS limita iz duty status event-a

**FMCSA Pravila:**
| Pravilo | Limit | Opis |
|---------|-------|------|
| 11-Hour Driving | 11h | Max vožnja u smeni |
| 14-Hour Shift | 14h | Max dužina smene |
| 30-Min Break | 8h | Obavezna pauza posle 8h vožnje |
| 60-Hour/7-Day | 60h | Ciklus za 7 dana |
| 70-Hour/8-Day | 70h | Ciklus za 8 dana |
| 34-Hour Restart | 34h | Reset ciklusa |

```kotlin
class HOSCalculator(
    private val cycleRule: CycleRule = CycleRule.US_70_HOUR_8_DAY
) {
    data class HOSCalculationResult(
        // Break (8h driving -> 30min break required)
        val breakTimeRemaining: Long,    // ms do obavezne pauze
        val timeSinceLastBreak: Long,    // ms od poslednje pauze

        // Drive (11h limit)
        val driveTimeRemaining: Long,    // ms preostalo
        val driveTimeUsed: Long,         // ms iskorišćeno

        // Shift (14h limit)
        val shiftTimeRemaining: Long,
        val shiftTimeUsed: Long,
        val shiftStartTime: Long?,

        // Cycle (60/70h over 7/8 days)
        val cycleTimeRemaining: Long,
        val cycleTimeUsed: Long,

        // Status
        val isInViolation: Boolean,
        val activeViolations: List<ViolationType>
    )

    fun calculate(
        events: List<DutyStatusEventEntity>,
        now: Long = System.currentTimeMillis()
    ): HOSCalculationResult {
        // Sortira evente hronološki
        val sorted = events.sortedBy { it.startTime }

        // Pronalazi početak trenutne smene
        val shiftStart = findShiftStart(sorted, now)

        // Računa vremena
        val driveTime = calculateDriveTime(sorted, shiftStart, now)
        val shiftTime = calculateShiftTime(sorted, shiftStart, now)
        val breakTime = calculateBreakTime(sorted, now)
        val cycleTime = calculateCycleTime(sorted, now)

        // Proverava 34h restart
        val restartTime = find34HourRestart(sorted, now)

        return HOSCalculationResult(...)
    }
}
```

### 5.2 HOSService.kt (~400 linija)

**Svrha:** Background servis za HOS kalkulacije i sync

```kotlin
class HOSService private constructor(context: Context) {
    private val calculator = HOSCalculator()
    private val database = ELDDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State
    private val _hosStatus = MutableStateFlow<HOSCalculationResult?>(null)
    val hosStatus: StateFlow<HOSCalculationResult?> = _hosStatus

    private val _currentDutyStatus = MutableStateFlow<String?>(null)
    val currentDutyStatus: StateFlow<String?> = _currentDutyStatus

    // Intervals
    private val RECALCULATE_INTERVAL_MS = 60_000L      // 1 minut
    private val SYNC_INTERVAL_MS = 5 * 60_000L         // 5 minuta
    private val INTERMEDIATE_EVENT_INTERVAL_MS = 60 * 60_000L  // 60 minuta

    fun start(token: String) {
        // Pokreni periodičnu rekalkulaciju
        scope.launch {
            while (isActive) {
                recalculateHOS()
                delay(RECALCULATE_INTERVAL_MS)
            }
        }

        // Pokreni periodični sync
        scope.launch {
            while (isActive) {
                syncHOSToBackend(token)
                delay(SYNC_INTERVAL_MS)
            }
        }

        // Intermediate events tokom vožnje (FMCSA zahtev)
        scope.launch {
            while (isActive) {
                if (currentDutyStatus.value == "DRIVING") {
                    sendIntermediateEvent(token)
                }
                delay(INTERMEDIATE_EVENT_INTERVAL_MS)
            }
        }
    }

    suspend fun recalculateNow() {
        val events = database.dutyStatusEventDao()
            .getEventsSince(System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L)
        _hosStatus.value = calculator.calculate(events)
    }
}
```

### 5.3 ViolationAnalyzer.kt (~400 linija)

**Svrha:** Detekcija HOS violation-a iz istorije event-a

```kotlin
class ViolationAnalyzer {
    enum class ViolationType {
        DRIVING_11_HOUR,        // Prekoračen 11h limit vožnje
        SHIFT_14_HOUR,          // Prekoračen 14h limit smene
        BREAK_30_MIN,           // Nije uzeta 30min pauza
        CYCLE_60_HOUR_7_DAY,    // Prekoračen 60h/7 dana
        CYCLE_70_HOUR_8_DAY,    // Prekoračen 70h/8 dana
        FORM_AND_MANNER,        // Neispravni unosi
        FALSE_LOG,              // Sumnjivi unosi
        ADVERSE_WEATHER_MISUSE, // Zloupotreba izuzetka
        SHORT_HAUL_EXCEPTION    // Kršenje short haul pravila
    }

    data class ViolationRecord(
        val type: ViolationType,
        val startTime: Long,
        val endTime: Long?,      // null = još traje
        val durationMinutes: Int,
        val description: String
    )

    fun analyzeViolations(
        events: List<DutyStatusEventEntity>,
        now: Long = System.currentTimeMillis()
    ): List<ViolationRecord> {
        val violations = mutableListOf<ViolationRecord>()

        // Analiziraj 11h driving
        violations.addAll(analyze11HourDriving(events, now))

        // Analiziraj 14h shift
        violations.addAll(analyze14HourShift(events, now))

        // Analiziraj 30min break
        violations.addAll(analyze30MinBreak(events, now))

        // Analiziraj cycle
        violations.addAll(analyzeCycle(events, now))

        return violations
    }
}
```

### 5.4 MalfunctionDetector.kt (~340 linija)

**Svrha:** Detekcija ELD malfunctions po FMCSA 49 CFR 395.34

```kotlin
class MalfunctionDetector private constructor(context: Context) {
    // FMCSA timing thresholds
    private val ENGINE_SYNC_TIMEOUT_MS = 5_000L      // 5 sekundi za ECM
    private val TIMING_DRIFT_THRESHOLD_MS = 10 * 60_000L  // 10 min drift
    private val GPS_ACQUISITION_TIMEOUT_MS = 60_000L  // 1 minut za GPS

    private val _activeMalfunctions = MutableStateFlow<Set<TickEventType>>(emptySet())
    val activeMalfunctions: StateFlow<Set<TickEventType>> = _activeMalfunctions

    fun start() {
        // Periodična provera svakog minuta
        scope.launch {
            while (isActive) {
                checkForMalfunctions()
                delay(60_000L)
            }
        }
    }

    private suspend fun checkForMalfunctions() {
        checkEngineSyncMalfunction()   // ECM podaci
        checkTimingMalfunction()        // Clock drift
        checkPositioningMalfunction()   // GPS
    }

    suspend fun reportPowerMalfunction() {
        triggerMalfunction(TickEventType.MALFUNCTION_POWER)
    }

    suspend fun clearPowerMalfunction() {
        clearMalfunction(TickEventType.MALFUNCTION_POWER)
    }
}
```

---

## 6. Location Modul - GPS i Lokacija

```
Lokacija: /app/src/main/java/com/eld/driver/location/
Fajlovi: 2
Linije: ~600
```

### 6.1 LocationService.kt (~350 linija)

**Svrha:** Dobijanje trenutne lokacije iz BLE uređaja ili telefona

```kotlin
class LocationService private constructor(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient
    private val bleManager = GeometrisWQManager.getInstance(context)

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float?,
        val source: LocationSource,  // BLE_DEVICE ili PHONE_GPS
        val timestamp: Long,
        val address: String?
    )

    enum class LocationSource {
        BLE_DEVICE,    // Prioritet - ELD GPS
        PHONE_GPS      // Fallback - telefon GPS
    }

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation

    fun getCurrentLocation(): LocationData? {
        // 1. Probaj BLE device lokaciju (prioritet)
        val eldData = bleManager.eldData.value
        if (eldData?.latitude != null && eldData.longitude != null) {
            return LocationData(
                latitude = eldData.latitude,
                longitude = eldData.longitude,
                source = LocationSource.BLE_DEVICE,
                timestamp = eldData.gpsTime ?: System.currentTimeMillis()
            )
        }

        // 2. Fallback na telefon GPS
        return _currentLocation.value
    }

    /**
     * Dobija FMCSA-compliant lokaciju string
     * Format: "X miles NE of Chicago, IL"
     */
    fun getFMCSALocation(latitude: Double, longitude: Double): String? {
        return fmcsaFormatter.format(latitude, longitude)
    }
}
```

### 6.2 FMCSALocationFormatter.kt (~250 linija)

**Svrha:** Formatiranje GPS koordinata po FMCSA 49 CFR 395.8

```kotlin
class FMCSALocationFormatter(private val cityDao: USCityDao) {
    /**
     * Format: "{X} miles {direction} of {city}, {state}"
     * Primer: "5 miles NE of Chicago, IL"
     */
    fun format(latitude: Double, longitude: Double): String? {
        // 1. Pronađi najbliži grad (population >= 5000)
        val nearestCity = findNearestCity(latitude, longitude)
            ?: return null

        // 2. Izračunaj udaljenost (Haversine formula)
        val distanceMiles = calculateDistanceMiles(
            latitude, longitude,
            nearestCity.latitude, nearestCity.longitude
        )

        // 3. Odredi pravac (N, NE, E, SE, S, SW, W, NW)
        val direction = calculateDirection(
            latitude, longitude,
            nearestCity.latitude, nearestCity.longitude
        )

        // 4. Formatiraj
        return "${distanceMiles.roundToInt()} miles $direction of ${nearestCity.name}, ${nearestCity.state}"
    }

    private fun findNearestCity(lat: Double, lon: Double): USCityEntity? {
        // Pretraga u krugu od 1° -> 2° -> 4° dok ne nađe
        for (radius in listOf(1.0, 2.0, 4.0)) {
            val cities = cityDao.getCitiesInBounds(
                lat - radius, lat + radius,
                lon - radius, lon + radius
            )
            if (cities.isNotEmpty()) {
                return cities.minByOrNull {
                    calculateDistanceMiles(lat, lon, it.latitude, it.longitude)
                }
            }
        }
        return null
    }

    private fun calculateDistanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine formula
        val R = 3958.8  // Earth radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }
}
```

---

## 7. Sync Modul - Offline Sinhronizacija

```
Lokacija: /app/src/main/java/com/eld/driver/sync/
Fajlovi: 1
Linije: ~1,000
```

### 7.1 SyncManager.kt

**Svrha:** Offline-first sinhronizacija sa backendom

```
┌─────────────────────────────────────────────────────────────────┐
│                     SyncManager Flow                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌─────────────┐                                               │
│   │ User Action │                                               │
│   └──────┬──────┘                                               │
│          │                                                       │
│          ▼                                                       │
│   ┌─────────────────────────────────────────────┐               │
│   │ 1. Save to Local DB (IMMEDIATELY)           │               │
│   │    - DutyStatusEventEntity                  │               │
│   │    - pendingSync = true                     │               │
│   └──────────────────────┬──────────────────────┘               │
│                          │                                       │
│                          ▼                                       │
│   ┌─────────────────────────────────────────────┐               │
│   │ 2. Add to Sync Queue                        │               │
│   │    - SyncQueueEntity                        │               │
│   │    - operationType, payload, retryCount     │               │
│   └──────────────────────┬──────────────────────┘               │
│                          │                                       │
│                          ▼                                       │
│          ┌───────────────┴───────────────┐                      │
│          │        isOnline?              │                      │
│          └───────────────┬───────────────┘                      │
│                 ┌────────┴────────┐                             │
│                 │                 │                              │
│            ┌────▼────┐      ┌─────▼─────┐                       │
│            │   YES   │      │    NO     │                       │
│            └────┬────┘      └─────┬─────┘                       │
│                 │                 │                              │
│                 ▼                 ▼                              │
│   ┌─────────────────────┐  ┌─────────────────────┐              │
│   │ 3. Process Queue    │  │ Wait for Network    │              │
│   │    - Send to API    │  │ (NetworkCallback)   │              │
│   │    - Mark as synced │  └──────────┬──────────┘              │
│   └─────────────────────┘             │                         │
│                                       │ Network Available        │
│                                       ▼                          │
│                          ┌─────────────────────────┐            │
│                          │ Process Queue (auto)    │            │
│                          └─────────────────────────┘            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Ključne Metode:**

```kotlin
class SyncManager private constructor(context: Context) {
    private val syncQueueDao: SyncQueueDao
    private val dutyStatusEventDao: DutyStatusEventDao
    private val apiService: ApiService

    // Network state
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    // ═══════════════════════════════════════════════
    // INITIAL SYNC (Na Login)
    // ═══════════════════════════════════════════════

    suspend fun performInitialSync(): Result<Unit> {
        // 1. Obriši sve lokalne evente (čist start)
        dutyStatusEventDao.deleteAll()
        syncQueueDao.clearQueue()

        // 2. Fetch evente za poslednjih 8 dana
        for (i in 0..7) {
            val date = formatDate(calendar.time)
            val response = apiService.getDriverEvents(token, date)
            if (response.isSuccessful) {
                val entities = response.body()?.data?.dutyStatusEvents?.map { it.toEntity() }
                dutyStatusEventDao.insertAll(entities)
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        // 3. Ako nema eventa, kreiraj OFF_DUTY za 8 dana
        if (dutyStatusEventDao.getMostRecentEvent() == null) {
            createOffDutyEventsForEmptyHistory()
        }

        // 4. Split multi-day evente
        splitMultiDayLocalEvents()

        // 5. Sync violations
        violationSyncService.analyzeAndSyncViolations()

        return Result.success(Unit)
    }

    // ═══════════════════════════════════════════════
    // PERIODIC SYNC (Svakih 5 min)
    // ═══════════════════════════════════════════════

    suspend fun performPeriodicSync() {
        if (!_isOnline.value) return

        // Samo PUSH lokalne promene na server
        // NE PULL-uje sa servera (izbegava konflikte)
        processQueue()
        violationSyncService.analyzeAndSyncViolations()
    }

    // ═══════════════════════════════════════════════
    // QUEUE OPERATIONS
    // ═══════════════════════════════════════════════

    suspend fun enqueueDutyStatusChange(request: DutyStatusChangeRequest, localEventId: String) {
        val queueItem = SyncQueueEntity(
            operationType = SyncOperationType.STATUS_CHANGE.name,
            payload = gson.toJson(StatusChangePayload(localEventId, request)),
            createdAt = System.currentTimeMillis()
        )
        syncQueueDao.enqueue(queueItem)

        // Ako online, sync odmah
        if (_isOnline.value) {
            scope.launch { processQueue() }
        }
    }

    suspend fun processQueue(): Int {
        val pendingItems = syncQueueDao.getRetryableItems()
        var processedCount = 0

        for (item in pendingItems) {
            val success = when (item.operationType) {
                "STATUS_CHANGE" -> syncStatusChange(item)
                "TICK_EVENT" -> syncTickEvent(item)
                else -> true
            }

            if (success) {
                syncQueueDao.remove(item)
                processedCount++
            } else {
                syncQueueDao.incrementRetry(item.id)
            }
        }

        return processedCount
    }

    // ═══════════════════════════════════════════════
    // NETWORK MONITORING
    // ═══════════════════════════════════════════════

    private fun observeConnectivity() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                scope.launch { processQueue() }  // Auto-sync
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        }
        connectivityManager.registerNetworkCallback(networkRequest, callback)
    }
}
```

---

## 8. UI Modul - Ekrani i Komponente

```
Lokacija: /app/src/main/java/com/eld/driver/ui/
Fajlovi: 25+
Linije: ~8,000
```

### 8.1 Struktura Ekrana

```
ui/
├── screens/
│   ├── login/
│   │   ├── LoginScreen.kt          # UI za login
│   │   └── LoginViewModel.kt       # Autentifikacija logika
│   │
│   ├── vehicle/
│   │   ├── VehicleSelectionScreen.kt
│   │   ├── VehicleConfirmationScreen.kt
│   │   └── VehicleViewModel.kt
│   │
│   ├── dashboard/
│   │   ├── DashboardScreen.kt      # Glavni radni ekran
│   │   └── DashboardViewModel.kt   # HOS, BLE, status
│   │
│   ├── logs/
│   │   ├── LogsScreen.kt           # Lista dnevnih logova
│   │   ├── LogDetailScreen.kt      # Detalji za dan
│   │   └── LogsViewModel.kt
│   │
│   ├── dvir/
│   │   ├── DVIRScreen.kt
│   │   ├── DriverInspectionScreen.kt
│   │   ├── VehicleDefectsScreen.kt
│   │   └── DVIRViewModel.kt
│   │
│   ├── inspection/
│   │   ├── InspectionListScreen.kt
│   │   ├── InspectionDetailScreen.kt
│   │   └── CreateInspectionScreen.kt
│   │
│   └── bletest/
│       ├── BleTestScreen.kt        # Debug ELD konekcije
│       └── BleTestViewModel.kt
│
├── components/
│   ├── HOSTimerCard.kt             # Kružni progress timer
│   ├── ChangeDutyStatusModal.kt    # Modal za promenu statusa
│   ├── TrailersModal.kt            # Modal za prikolice
│   ├── CurvedWaveShape.kt          # Custom shape
│   └── SideMenuDrawer.kt           # Navigacioni meni
│
└── theme/
    ├── Color.kt                     # Paleta boja
    ├── Dimensions.kt                # Spacing, corner radius
    ├── Type.kt                      # Typography
    └── Theme.kt                     # Material 3 tema
```

### 8.2 Dashboard Screen (Glavni Ekran)

```kotlin
@Composable
fun DashboardScreen(
    navController: NavController,
    authToken: String,
    viewModel: DashboardViewModel = viewModel()
) {
    // State
    val currentStatus by viewModel.currentDutyStatus.collectAsState()
    val hosStatus by viewModel.hosStatus.collectAsState()
    val eldConnection by viewModel.eldConnectionStatus.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()

    Scaffold(
        drawerContent = { SideMenuDrawer(...) }
    ) {
        Column {
            // Header sa ELD statusom
            TopBar(
                userName = currentUser?.fullName,
                eldStatus = eldConnection,
                onMenuClick = { openDrawer() }
            )

            // Curved wave sa trenutnim statusom
            CurvedStatusSection(
                currentStatus = currentStatus,
                onChangeStatus = { showStatusModal = true }
            )

            // HOS Timer kartice
            Row {
                HOSTimerCard(
                    title = "Break",
                    remaining = hosStatus.breakRemaining,
                    progress = hosStatus.breakProgress,
                    hasViolation = hosStatus.breakViolation
                )
                HOSTimerCard(
                    title = "Drive",
                    remaining = hosStatus.driveRemaining,
                    progress = hosStatus.driveProgress,
                    hasViolation = hosStatus.driveViolation
                )
            }
            Row {
                HOSTimerCard(
                    title = "Shift",
                    remaining = hosStatus.shiftRemaining,
                    progress = hosStatus.shiftProgress
                )
                HOSTimerCard(
                    title = "Cycle",
                    remaining = hosStatus.cycleRemaining,
                    progress = hosStatus.cycleProgress
                )
            }

            // Sync indicator
            if (pendingSyncCount > 0) {
                Text("$pendingSyncCount pending sync")
            }
        }
    }

    // Modal za promenu statusa
    if (showStatusModal) {
        ChangeDutyStatusModal(
            currentStatus = currentStatus,
            allowYardMove = viewModel.isYardMoveAllowed,
            allowPersonalConveyance = viewModel.isPersonalConveyanceAllowed,
            onStatusSelected = { newStatus, note ->
                viewModel.changeDutyStatus(authToken, newStatus, note)
            },
            onDismiss = { showStatusModal = false }
        )
    }
}
```

### 8.3 HOSTimerCard Komponenta

```kotlin
@Composable
fun HOSTimerCard(
    title: String,
    remaining: String,        // "08:30"
    progress: Float,          // 0.0 - 1.0
    hasViolation: Boolean = false,
    showClock: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasViolation) ErrorLight else Color.White
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Naslov
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            // Kružni progress
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(100.dp)) {
                    // Background arc
                    drawArc(
                        color = Color(0xFFE5E7EB),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Progress arc
                    val progressColor = when {
                        hasViolation -> ErrorRed
                        progress < 0.25f -> WarningOrange
                        else -> SuccessGreen
                    }
                    drawArc(
                        color = progressColor,
                        startAngle = 135f,
                        sweepAngle = 270f * progress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Vreme u centru
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = remaining,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasViolation) ErrorRed else TextPrimary
                    )
                }
            }
        }
    }
}
```

### 8.4 Log Detail Screen sa Grafom

```kotlin
@Composable
private fun ELDGraph(
    events: List<DutyStatusEventDto>,
    logDate: String,
    companyTimeZone: TimeZone,
    modifier: Modifier = Modifier
) {
    // Graf ima 4 reda (od gore ka dole):
    // Row 0: OFF DUTY
    // Row 1: SLEEPER BERTH
    // Row 2: DRIVING
    // Row 3: ON DUTY

    Canvas(modifier = modifier.height(80.dp)) {
        val width = size.width
        val height = size.height
        val rowHeight = height / 4

        // Crtaj grid linije
        for (i in 0..4) {
            drawLine(color = gridColor, start = Offset(0f, i * rowHeight), end = Offset(width, i * rowHeight))
        }
        for (i in 0..24) {
            val x = (i / 24f) * width
            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height))
        }

        // Crtaj ELD status linije
        events.forEachIndexed { index, event ->
            val rowIndex = getRowIndex(event.dutyStatus)
            val y = rowIndex * rowHeight + rowHeight / 2

            val startHours = event.getStartTimeHours(companyTimeZone)
            val durationHours = (event.durationMinutes ?: 0) / 60f
            var endHours = startHours + durationHours

            // Handle aktivnih eventa
            if (event.isActive || event.durationMinutes == null) {
                endHours = if (isToday) currentDecimalHours else 24f
            }

            val startX = (startHours / 24f) * width
            val endX = (minOf(endHours, 24f) / 24f) * width

            // Horizontalna linija (trajanje)
            drawLine(
                color = lineColor,
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = 4.5f
            )

            // Vertikalna linija (tranzicija na sledeći status)
            if (index < events.size - 1) {
                val nextEvent = events[index + 1]
                val nextY = getRowIndex(nextEvent.dutyStatus) * rowHeight + rowHeight / 2
                if (nextY != y) {
                    drawLine(
                        color = lineColor,
                        start = Offset(endX, y),
                        end = Offset(endX, nextY),
                        strokeWidth = 1.5f
                    )
                }
            }
        }

        // Narandžasta linija za trenutno vreme (samo za danas)
        if (isToday) {
            val currentX = (currentDecimalHours / 24f) * width
            drawLine(
                color = Color(0xFFF97316),
                start = Offset(currentX, 0f),
                end = Offset(currentX, height),
                strokeWidth = 2f
            )
        }
    }
}
```

---

## 9. FMCSA Usklađenost

### 9.1 Implementirana Pravila

| FMCSA Pravilo | CFR Referenca | Implementacija |
|---------------|---------------|----------------|
| 11-Hour Driving Limit | 395.3(a)(3)(i) | HOSCalculator |
| 14-Hour Shift Limit | 395.3(a)(2) | HOSCalculator |
| 30-Minute Break | 395.3(a)(3)(ii) | HOSCalculator |
| 60-Hour/7-Day Limit | 395.3(b)(1) | HOSCalculator |
| 70-Hour/8-Day Limit | 395.3(b)(2) | HOSCalculator |
| 34-Hour Restart | 395.3(c) | HOSCalculator |
| Location Recording | 395.8(a)(1) | FMCSALocationFormatter |
| Intermediate Events | 395.26(h) | HOSService (svakih 60min) |
| ELD Malfunctions | 395.34 | MalfunctionDetector |
| Data Transfer | 395.36 | SyncManager |

### 9.2 Malfunction Detekcija (49 CFR 395.34)

```kotlin
// Power Compliance Malfunction
// - Detektuje se kada ELD izgubi napajanje
bleManager.onEldDisconnected = {
    malfunctionDetector.reportPowerMalfunction()
}

// Engine Synchronization Malfunction
// - ELD mora da primi ECM podatke u roku od 5 sekundi
private suspend fun checkEngineSyncMalfunction() {
    val startTime = engineStartTime ?: return
    if (!hasReceivedEcmData && System.currentTimeMillis() - startTime > 5000) {
        triggerMalfunction(TickEventType.MALFUNCTION_ENGINE_SYNC)
    }
}

// Positioning Compliance Malfunction
// - GPS mora da bude dostupan
private suspend fun checkPositioningMalfunction() {
    val location = locationService.getCurrentLocation()
    if (location == null && System.currentTimeMillis() - lastGpsTime > 60_000) {
        triggerMalfunction(TickEventType.MALFUNCTION_POSITIONING)
    }
}
```

### 9.3 Location Format (49 CFR 395.8)

```
Format: "{X} miles {direction} of {city}, {state}"
Primer: "5 miles NE of Chicago, IL"

Zahtevi:
- Gradovi sa populacijom >= 5,000
- Udaljenost u miljama
- Kompasni pravac (N, NE, E, SE, S, SW, W, NW)
- Offline baza sa 7,000+ gradova
```

---

## 10. Tehnički Stack

### Jezici i Frameworks
| Tehnologija | Verzija | Svrha |
|-------------|---------|-------|
| Kotlin | 1.9.x | Primarni jezik |
| Jetpack Compose | 1.5.x | UI framework |
| Material 3 | 1.2.x | Design sistem |
| Room | 2.6.x | Lokalna baza |
| Retrofit | 2.9.x | HTTP klijent |

### Android Komponente
| Komponenta | Svrha |
|------------|-------|
| ViewModel | State management |
| StateFlow/Flow | Reactive data |
| Navigation Compose | Navigacija |
| WorkManager | Background tasks |
| Bluetooth LE | ELD komunikacija |

### Eksterne Biblioteke
| Biblioteka | Svrha |
|------------|-------|
| Geometris wqlib v1.0.10 | ELD SDK |
| OkHttp | HTTP logging |
| Gson | JSON serialization |
| Google Play Location | GPS |

### Gradle Dependencies
```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Geometris ELD SDK
    implementation(files("libs/wqlib-1.0.10.aar"))
}
```

---

## Dodatak: Struktura Fajlova

```
com/eld/driver/
├── MainActivity.kt                 # Entry point
├── ELDApp.kt                       # Navigation host
├── ELDDriverApplication.kt         # Application class
│
├── ble/                            # Bluetooth ELD
│   ├── GeometrisWQManager.kt       # Main BLE manager
│   ├── GeometrisDataParser.kt      # Packet parsing
│   ├── GeometrisBleService.kt      # BLE constants
│   └── models/
│       ├── BleConnectionState.kt
│       ├── BleDataState.kt
│       ├── GeometrisEldData.kt
│       └── UnidentifiedEvent.kt
│
├── data/                           # Data layer
│   ├── api/
│   │   ├── ApiService.kt           # Retrofit interface
│   │   └── ApiConfig.kt
│   ├── models/
│   │   ├── User.kt
│   │   ├── Vehicle.kt
│   │   ├── DutyStatus.kt
│   │   ├── HOSStatus.kt
│   │   ├── DailyLog.kt
│   │   └── DVIRInspection.kt
│   ├── local/
│   │   ├── ELDDatabase.kt          # Room database
│   │   ├── TokenManager.kt         # Token storage
│   │   ├── USCitiesDataLoader.kt
│   │   ├── dao/                    # Data Access Objects
│   │   └── entity/                 # Room entities
│   └── repository/
│       └── ELDRepository.kt        # Single source of truth
│
├── hos/                            # Hours of Service
│   ├── HOSCalculator.kt            # HOS calculation
│   ├── HOSService.kt               # Background service
│   ├── ViolationAnalyzer.kt        # Violation detection
│   ├── MalfunctionDetector.kt      # ELD malfunctions
│   └── ViolationSyncService.kt
│
├── location/                       # GPS & Location
│   ├── LocationService.kt          # Location provider
│   └── FMCSALocationFormatter.kt   # FMCSA format
│
├── sync/                           # Offline sync
│   └── SyncManager.kt              # Queue-based sync
│
└── ui/                             # User Interface
    ├── screens/
    │   ├── login/
    │   ├── vehicle/
    │   ├── dashboard/
    │   ├── logs/
    │   ├── dvir/
    │   ├── inspection/
    │   └── bletest/
    ├── components/
    │   ├── HOSTimerCard.kt
    │   ├── ChangeDutyStatusModal.kt
    │   └── ...
    └── theme/
        ├── Color.kt
        ├── Type.kt
        └── Theme.kt
```

---

> **Generisano:** Claude AI
> **Projekat:** ELD Driver Android
> **Linije koda:** 22,148
> **Kotlin fajlova:** 75
