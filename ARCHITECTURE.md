# ELD Driver Android - Arhitektura i Use Cases

## Pregled

ELD Driver Android je **offline-first** mobilna aplikacija za vozače komercijalnih vozila koja prati Hours of Service (HOS) prema FMCSA regulativama. Aplikacija se integriše sa Geometris ELD uređajima putem Bluetooth Low Energy (BLE) i automatski prati duty status vozača.

---

## Arhitektura

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │Dashboard │ │  Logs    │ │  DVIR    │ │ Vehicle  │        │
│  │ Screen   │ │  Screen  │ │  Screen  │ │  Screen  │        │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘        │
│       │            │            │            │               │
│  ┌────┴─────┐ ┌────┴─────┐ ┌────┴─────┐ ┌────┴─────┐        │
│  │Dashboard │ │  Logs    │ │  DVIR    │ │ Vehicle  │        │
│  │ViewModel│ │ ViewModel│ │ ViewModel│ │ ViewModel│        │
│  └────┬─────┘ └────┬─────┘ └────┴─────┘ └────┴─────┘        │
└───────┼────────────┼────────────────────────────────────────┘
        │            │
┌───────┴────────────┴────────────────────────────────────────┐
│                    Domain Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │  ELDRepository  │  │   HOSService    │                   │
│  │ (Single Source  │  │  (HOS Engine)   │                   │
│  │   of Truth)     │  │                 │                   │
│  └────────┬────────┘  └────────┬────────┘                   │
│           │                    │                             │
│  ┌────────┴────────┐  ┌────────┴────────┐                   │
│  │   SyncManager   │  │  HOSCalculator  │                   │
│  │ (Offline Sync)  │  │  (FMCSA Rules)  │                   │
│  └─────────────────┘  └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
        │
┌───────┴─────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │   Room Database │  │    ApiService   │                   │
│  │   (SQLite)      │  │   (Retrofit)    │                   │
│  └─────────────────┘  └─────────────────┘                   │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │  TokenManager   │  │ LocationService │                   │
│  │ (JWT + Prefs)   │  │   (GPS)         │                   │
│  └─────────────────┘  └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
        │
┌───────┴─────────────────────────────────────────────────────┐
│                   Hardware Layer                             │
│  ┌─────────────────────────────────────┐                    │
│  │       GeometrisWQManager            │                    │
│  │  (BLE Connection to ELD Device)     │                    │
│  └─────────────────────────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Offline-First Strategija

### Princip
1. **Lokalna baza je source of truth** za trenutnu sesiju
2. **Sync queue** čuva sve promene za slanje na server
3. **Background sync** šalje podatke kad je online
4. **Server data** se koristi za istorijske podatke

### Implementacija za Logs

```kotlin
fun loadLogDetail(token: String, date: String) {
    val isToday = dateStr == todayStr

    if (isToday) {
        // DANAS: Uvek lokalna baza (može imati unsynced evente)
        loadEventsFromLocal(date)
    } else {
        // PROŠLI DANI: Server first, fallback na local
        try {
            apiService.getDriverEvents(token, dateStr)
        } catch (e: Exception) {
            loadEventsFromLocal(date)
        }
    }
}
```

---

## Use Cases

### 1. Login Flow

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│  User   │────>│  Login  │────>│ Backend │────>│  JWT    │
│  Input  │     │  API    │     │  Auth   │     │  Token  │
└─────────┘     └─────────┘     └─────────┘     └────┬────┘
                                                      │
                    ┌─────────────────────────────────┘
                    │
              ┌─────▼─────┐
              │  Parse    │
              │  JWT      │
              └─────┬─────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────▼────┐   ┌─────▼─────┐  ┌─────▼─────┐
│ Driver  │   │  Company  │  │ Timezone  │
│  Data   │   │    ID     │  │  Offset   │
└─────────┘   └───────────┘  └───────────┘
```

**JWT Claims:**
```json
{
  "DriverData": {
    "CompanyId": 38,
    "CompanyTimeOffset": -8.0,  // PST (UTC-8)
    "AllowYardMove": true,
    "AllowPersonalConveyance": true
  }
}
```

**Posle login-a:**
1. Token se čuva u `TokenManager`
2. `CompanyTimeOffset` se ekstraktuje za timezone prikaz
3. `HOSService.start()` pokreće periodično računanje
4. `performInitialSync()` povlači podatke sa servera
5. LOGIN tick event se šalje na backend

---

### 2. Duty Status Change

**Tipovi statusa:**
| Status | Opis | Utiče na HOS |
|--------|------|--------------|
| `OFF_DUTY` | Van dužnosti | Reset za 10h+ |
| `SLEEPER_BERTH` | Spavanje | Reset za 10h+ |
| `DRIVING` | Vožnja | Troši Drive + Shift + Cycle |
| `ON_DUTY_NOT_DRIVING` | Na dužnosti | Troši Shift + Cycle |
| `YARD_MOVE` | Pomeranje u dvorištu | Troši Shift + Cycle |
| `PERSONAL_CONVEYANCE` | Lična vožnja | Ne utiče |

**Flow promene statusa:**

```
User clicks status button
         │
         ▼
┌─────────────────┐
│ DashboardVM     │
│ changeDutyStatus│
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ ELDRepository   │────>│ HOSService      │
│ changeDutyStatus│     │ onDutyStatus    │
└────────┬────────┘     │ Changed         │
         │              └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ Save to Local   │     │ Close previous  │
│ Database        │     │ event (endTime) │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ Add to Sync     │     │ Recalculate     │
│ Queue           │     │ HOS             │
└────────┬────────┘     └─────────────────┘
         │
         ▼
┌─────────────────┐
│ SyncManager     │
│ processQueue    │──────> Backend API
└─────────────────┘
```

---

### 3. HOS Calculation

**FMCSA Pravila (Property-Carrying):**

| Pravilo | Limit | Reset |
|---------|-------|-------|
| Drive Time | 11 sati | 10h odmora |
| Shift Time | 14 sati | 10h odmora |
| Break | 30 min posle 8h vožnje | 30 min pauza |
| Cycle (70h/8d) | 70 sati u 8 dana | 34h restart |

**Kalkulacija (HOSCalculator.kt):**

```kotlin
fun calculate(events: List<DutyStatusEventEntity>): HOSCalculationResult {
    // 1. Pronađi početak shift-a (posle 10h+ odmora)
    val shiftStart = findShiftStart(events, now)

    // 2. Izračunaj vreme vožnje u shiftu
    val driveTime = calculateDriveTime(shiftEvents, now)

    // 3. Izračunaj trajanje shift-a
    val shiftDuration = now - shiftStart

    // 4. Izračunaj vreme od poslednje pauze
    val timeSinceBreak = calculateDriveTimeSinceLastBreak(shiftEvents, now)

    // 5. Izračunaj cycle sate (sa 34h restart check)
    val cycleHours = calculateCycleHours(events, now)

    // 6. Proveri violations
    val violations = checkViolations(driveTime, shiftDuration, timeSinceBreak, cycleHours)

    return HOSCalculationResult(
        driveTimeRemainingMs = DRIVE_LIMIT_MS - driveTime,      // 11h - used
        shiftTimeRemainingMs = SHIFT_LIMIT_MS - shiftDuration,  // 14h - used
        breakTimeRemainingMs = BREAK_REQUIRED_AFTER_MS - timeSinceBreak,  // 8h - since break
        cycleTimeRemainingMs = cycleLimitMs - cycleHours,       // 70h - used
        violations = violations
    )
}
```

**Periodično računanje:**
- Svaki **1 minut** se računa HOS
- Svaki **5 minuta** se sync-uje sa backend-om
- Posle **svake promene statusa** se odmah računa

---

### 4. HOS Violations

**Tipovi violations:**

| Violation | Uslov | Završava se kada |
|-----------|-------|------------------|
| `DRIVE_TIME_EXCEEDED` | driveTime > 11h | 10h+ odmora (novi shift) |
| `SHIFT_TIME_EXCEEDED` | shiftDuration > 14h | 10h+ odmora (novi shift) |
| `BREAK_REQUIRED` | driveSinceBreak > 8h | 30+ min pauza |
| `CYCLE_TIME_EXCEEDED` | cycleHours > 70h | 34h restart ili rolloff |

**Violation Lifecycle:**

```
                    ┌──────────────────┐
                    │  HOS Calculation │
                    │  detects limit   │
                    │  exceeded        │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ handleViolation  │
                    │ Changes()        │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
     ┌────────────────┐           ┌────────────────┐
     │ NEW Violation  │           │ ENDED Violation│
     └───────┬────────┘           └───────┬────────┘
             │                            │
             ▼                            ▼
     ┌────────────────┐           ┌────────────────┐
     │ POST /hos/     │           │ PUT /hos/      │
     │ violations     │           │ violations/end │
     │ {startTime}    │           │ {endTime}      │
     └───────┬────────┘           └────────────────┘
             │
             ▼
     ┌────────────────┐
     │ Save to:       │
     │ - activeViol.  │
     │ - startTimes   │
     │ - database     │
     └────────────────┘
```

**Perzistencija violations (rešen bug):**

```kotlin
// HOSStatusEntity čuva:
val violations: String = ""                    // "DRIVE_TIME_EXCEEDED,BREAK_REQUIRED"
val violationStartTimesJson: String = ""       // {"DRIVE_TIME_EXCEEDED":1702123456789}

// Pri restart-u app-a, loadCachedHOS() restaurira:
activeViolations.addAll(cachedViolations)
violationStartTimes.putAll(cached.getViolationStartTimesMap())
```

---

### 5. Logs Display

**Graf na Log Detail Screen:**

```
    OFF_DUTY  ████████████████
    SLEEPER
    DRIVING              ████████████████████
    ON_DUTY                                   ████
              |----|----|----|----|----|----|----|----|
              0    3    6    9   12   15   18   21   24
```

**Kalkulacija pozicije na grafu:**

```kotlin
// Frontend pristup (pravilno):
val startHours = event.getStartTimeHours(companyTimeZone)
val durationHours = (event.durationMinutes ?: 0) / 60f
val endHours = startHours + durationHours  // NE parsira endTime!

// Clamp na granice dana
val finalEndHours = minOf(endHours, 24f)
```

**CLAMP logika za evente koji prelaze dan:**

```kotlin
// Event koji je počeo juče u 22:00 i traje do danas 10:00
// Za prikaz DANAS:

clampedStartTime = max(event.startTime, todayMidnight)  // = todayMidnight
clampedEndTime = min(event.endTime, tomorrowMidnight)   // = danas 10:00

// Na grafu: prikazuje se od 00:00 do 10:00
startHours = 0.0
endHours = 10.0
```

**Carry-forward event (kada nema logova danas):**

```kotlin
// Ako nema overlapping eventa za danas, uzmi poslednji poznati status
if (relevantEvents.isEmpty()) {
    val lastEvent = repository.getCurrentDutyStatus()
    if (lastEvent != null && lastEvent.startTime < dayStart) {
        // Koristi kao carry-forward, prikaži od ponoći do sada
        relevantEvents = listOf(lastEvent)
    }
}
```

---

### 6. BLE/ELD Connection (Geometris)

**Connection Flow:**

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Scan for  │────>│  Connect to │────>│  Discover   │
│   Devices   │     │   Device    │     │  Services   │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                    ┌──────────────────────────┘
                    │
              ┌─────▼─────┐
              │ Subscribe │
              │ to RPM    │
              │ Notify    │
              └─────┬─────┘
                    │
         ┌──────────┴──────────┐
         │                     │
    ┌────▼────┐          ┌─────▼─────┐
    │ RPM > 0 │          │ RPM == 0  │
    │ Vehicle │          │ Vehicle   │
    │ Moving  │          │ Stopped   │
    └────┬────┘          └─────┬─────┘
         │                     │
         ▼                     ▼
┌─────────────────┐   ┌─────────────────┐
│ Auto switch to  │   │ Keep current    │
│ DRIVING status  │   │ status          │
└─────────────────┘   └─────────────────┘
```

**RPM Threshold:**
```kotlin
private const val RPM_DRIVING_THRESHOLD = 50  // RPM iznad ovog = vožnja
```

**Automatic Status Changes:**
- Kad RPM pređe threshold → automatski DRIVING
- DRIVING status je **LOCKED** dok je RPM > 0 (ne može se ručno promeniti)

---

### 7. Sync Queue

**Offline-first sync:**

```
┌─────────────────┐
│ User changes    │
│ duty status     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Save to Local   │────>│ Add to Sync     │
│ Database        │     │ Queue           │
└─────────────────┘     └────────┬────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
              ┌─────▼─────┐            ┌──────▼──────┐
              │  Online?  │            │  Offline?   │
              └─────┬─────┘            └──────┬──────┘
                    │                         │
                    ▼                         ▼
           ┌────────────────┐        ┌────────────────┐
           │ Process Queue  │        │ Wait for       │
           │ Immediately    │        │ connectivity   │
           └────────────────┘        └────────────────┘
                    │
                    ▼
           ┌────────────────┐
           │ POST to API    │
           │ Mark as synced │
           └────────────────┘
```

**Sync Queue Entity:**
```kotlin
data class SyncQueueEntity(
    val id: String,           // UUID
    val entityType: String,   // "DUTY_STATUS" | "TICK_EVENT"
    val entityId: String,     // Local ID
    val payload: String,      // JSON
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)
```

---

### 8. Timezone Handling

**Company Timezone iz JWT:**

```kotlin
// JWT claim: DriverData.CompanyTimeOffset = -8.0 (PST)

// TokenManager ekstraktuje:
val offsetHours = driverDataObj.companyTimeOffset ?: 0.0  // -8.0
val offsetMinutes = (offsetHours * 60).toInt()            // -480
prefs.putInt(KEY_TIMEZONE_OFFSET, offsetMinutes)

// Korišćenje u app-u:
val companyTimeZone = TimeZone.getTimeZone("GMT${if (offset >= 0) "+" else ""}${offset/60}")
```

**Prikaz vremena:**
- Svi timestamp-i se čuvaju kao **UTC epoch milliseconds**
- Za prikaz se konvertuju u **company timezone**
- Graf prikazuje 24h u company timezone

---

### 9. DVIR (Driver Vehicle Inspection Report)

**Inspection Types:**
- `PRE_TRIP` - Pre vožnje
- `POST_TRIP` - Posle vožnje

**Flow:**

```
┌─────────────────┐
│ Select Vehicle  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Choose          │
│ Inspection Type │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ Select Defects  │────>│ Add Comments    │
│ (if any)        │     │ (optional)      │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     │
                     ▼
            ┌─────────────────┐
            │ Submit to       │
            │ Backend         │
            └─────────────────┘
```

---

## Database Schema

```sql
-- Duty Status Events
CREATE TABLE duty_status_events (
    id TEXT PRIMARY KEY,
    dutyStatus TEXT NOT NULL,        -- OFF_DUTY, DRIVING, etc.
    startTime INTEGER NOT NULL,      -- UTC millis
    endTime INTEGER,                 -- UTC millis (NULL if active)
    durationMinutes INTEGER,
    location TEXT,
    latitude REAL,
    longitude REAL,
    odometer REAL,
    engineHours REAL,
    vehicleId INTEGER,
    note TEXT,
    isActive INTEGER DEFAULT 0,      -- 1 if current status
    isSynced INTEGER DEFAULT 0,
    pendingSync INTEGER DEFAULT 1,
    serverId INTEGER,
    localCreatedAt INTEGER
);

-- HOS Status (Singleton)
CREATE TABLE hos_status (
    id INTEGER PRIMARY KEY DEFAULT 1,
    driveTimeRemainingSeconds INTEGER,
    shiftTimeRemainingSeconds INTEGER,
    breakTimeRemainingSeconds INTEGER,
    cycleTimeRemainingSeconds INTEGER,
    driveTimeUsedSeconds INTEGER,
    shiftTimeUsedSeconds INTEGER,
    breakTimeDrivingSeconds INTEGER,
    cycleTimeUsedSeconds INTEGER,
    shiftStartTime INTEGER,
    lastBreakEndTime INTEGER,
    violations TEXT,                 -- Comma-separated
    violationStartTimesJson TEXT,    -- JSON map
    lastCalculatedAt INTEGER,
    currentDutyStatus TEXT
);

-- Sync Queue
CREATE TABLE sync_queue (
    id TEXT PRIMARY KEY,
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    payload TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    retryCount INTEGER DEFAULT 0,
    lastError TEXT
);

-- Daily Logs
CREATE TABLE daily_logs (
    date TEXT PRIMARY KEY,           -- yyyy-MM-dd
    dayOfWeek TEXT,
    month TEXT,
    day INTEGER,
    recapHours INTEGER,
    recapMinutes INTEGER,
    defectsCount INTEGER,
    distanceMiles REAL,
    isCertified INTEGER,
    hasInspections INTEGER,
    violationCount INTEGER,
    violationsJson TEXT,
    formMannerErrorCount INTEGER,
    formMannerErrorsJson TEXT,
    inspectionCount INTEGER
);
```

---

## API Endpoints

| Method | Endpoint | Opis |
|--------|----------|------|
| POST | `/api/mobile/auth/login` | Login |
| GET | `/api/mobile/drivers/logs` | Lista logova |
| GET | `/api/mobile/drivers/events?date=` | Eventi za dan |
| POST | `/api/mobile/drivers/events/status` | Promeni status |
| POST | `/api/mobile/drivers/events/tick` | Tick event |
| GET | `/api/mobile/drivers/hos` | Trenutni HOS |
| POST | `/api/mobile/drivers/hos/sync` | Sync HOS |
| POST | `/api/mobile/drivers/hos/violations` | Nova violation |
| PUT | `/api/mobile/drivers/hos/violations/end` | Završi violation |
| GET | `/api/mobile/vehicles` | Lista vozila |
| POST | `/api/mobile/inspections` | Kreiraj DVIR |

---

## Error Handling

**Retry Strategy:**
```kotlin
// SyncManager retry logic
val MAX_RETRY_COUNT = 3
val RETRY_DELAY_MS = 5000L

if (response.isSuccessful) {
    syncQueueDao.delete(item)
} else {
    if (item.retryCount < MAX_RETRY_COUNT) {
        syncQueueDao.updateRetry(item.id, item.retryCount + 1, errorMessage)
    } else {
        syncQueueDao.delete(item)  // Give up after 3 retries
        Log.e(TAG, "Sync failed permanently: ${item.id}")
    }
}
```

---

## Testing Checklist

### HOS Violations
- [ ] 11h vožnje → DRIVE_TIME_EXCEEDED počinje
- [ ] 10h odmora → DRIVE_TIME_EXCEEDED završava
- [ ] 8h vožnje bez pauze → BREAK_REQUIRED počinje
- [ ] 30min pauza → BREAK_REQUIRED završava
- [ ] App restart → violations se restauriraju
- [ ] Offline → violations se čuvaju lokalno
- [ ] Online → violations se šalju na backend

### Logs Display
- [ ] Današnji dan → lokalna baza
- [ ] Prošli dan → server API
- [ ] Event preko ponoći → pravilno clampovan
- [ ] Nema eventa danas → carry-forward prikazan
- [ ] Timezone konverzija → tačna

### Sync
- [ ] Offline promene → u sync queue
- [ ] Online → queue se procesira
- [ ] Retry → 3 pokušaja
- [ ] Logout → sync pre clear-a

---

## Version History

| Verzija | Datum | Promene |
|---------|-------|---------|
| DB v4 | 2024-12 | Dodato `violationStartTimesJson` |
| DB v3 | 2024-12 | Dodato `DailyLogEntity` |
| DB v2 | 2024-11 | Dodato `USCityEntity` |
| DB v1 | 2024-11 | Inicijalna schema |
