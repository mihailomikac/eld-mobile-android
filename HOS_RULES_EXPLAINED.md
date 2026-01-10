# FMCSA Hours of Service (HOS) Pravila - Kompletno Objašnjenje

> **Regulativa:** 49 CFR Part 395
> **Primenjuje se na:** Komercijalne vozače kamiona (CMV) u međudržavnom transportu

---

## Sadržaj

1. [Osnove HOS Sistema](#1-osnove-hos-sistema)
2. [11-Hour Driving Limit](#2-11-hour-driving-limit)
3. [14-Hour Shift Limit](#3-14-hour-shift-limit)
4. [30-Minute Break Rule](#4-30-minute-break-rule)
5. [60/70-Hour Cycle Limit](#5-6070-hour-cycle-limit)
6. [34-Hour Restart](#6-34-hour-restart)
7. [Sleeper Berth Provisions](#7-sleeper-berth-provisions)
8. [Personal Conveyance](#8-personal-conveyance)
9. [Yard Move](#9-yard-move)
10. [Violations - Tipovi i Primeri](#10-violations---tipovi-i-primeri)
11. [Praktični Primeri Scenarija](#11-praktični-primeri-scenarija)

---

## 1. Osnove HOS Sistema

### Četiri Duty Statusa

| Status | Kod | Opis | Utiče na HOS? |
|--------|-----|------|---------------|
| **OFF DUTY** | OFF | Vozač nije na dužnosti, slobodno vreme | NE - resetuje timere |
| **SLEEPER BERTH** | SB | Vozač spava u kabini kamiona | NE - resetuje timere |
| **DRIVING** | D | Vozač aktivno vozi vozilo | DA - troši sve timere |
| **ON DUTY NOT DRIVING** | ON | Na poslu ali ne vozi (utovar, papiri, čekanje) | DA - troši Shift i Cycle |

### Kako Se Računa Vreme

```
DRIVING time     = Samo vreme provedeno u statusu DRIVING
ON DUTY time     = DRIVING + ON_DUTY_NOT_DRIVING
OFF DUTY time    = OFF_DUTY + SLEEPER_BERTH
```

### Ključni Pojmovi

| Pojam | Definicija |
|-------|------------|
| **Shift** | Period od kad vozač počne raditi do kad uzme 10h odmor |
| **Cycle** | Kumulativno ON DUTY vreme u poslednjih 7 ili 8 dana |
| **Break** | Pauza od minimum 30 minuta (OFF ili SB) |
| **Restart** | 34 uzastopna sata OFF DUTY koji resetuju Cycle |

---

## 2. 11-Hour Driving Limit

### Pravilo
> Vozač sme voziti **maksimalno 11 sati** nakon 10 uzastopnih sati van dužnosti.

### Kako Funkcioniše

```
┌─────────────────────────────────────────────────────────────────┐
│                    11-HOUR DRIVING LIMIT                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  10h OFF DUTY ──▶ RESET ──▶ Imaš 11h za vožnju                  │
│                                                                  │
│  ════════════════════════════════════════════                   │
│  0h        3h        6h        9h       11h                     │
│  ├─────────┼─────────┼─────────┼─────────┤                     │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓                     │
│  Dostupno za vožnju                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer 1: Normalan Dan

```
06:00 - Vozač završava 10h OFF DUTY (spavao od 20:00 prethodnog dana)
        ✓ RESET: Ima 11h za vožnju

06:00 - 06:30  ON DUTY (pregled vozila)      → Drive: 11:00 ostalo
06:30 - 10:30  DRIVING (4 sata)              → Drive: 07:00 ostalo
10:30 - 11:00  ON DUTY (utovar)              → Drive: 07:00 ostalo (ne troši)
11:00 - 15:00  DRIVING (4 sata)              → Drive: 03:00 ostalo
15:00 - 15:30  OFF DUTY (pauza)              → Drive: 03:00 ostalo
15:30 - 18:30  DRIVING (3 sata)              → Drive: 00:00 ostalo

18:30 - MORA DA STANE! Iskoristio svih 11h vožnje.
```

### Primer 2: Violation - Prekoračenje 11h

```
06:00 - Vozač završava 10h OFF DUTY
        ✓ RESET: Ima 11h za vožnju

06:00 - 12:00  DRIVING (6 sati)              → Drive: 05:00 ostalo
12:00 - 12:30  ON DUTY (pauza za ručak)      → Drive: 05:00 ostalo
12:30 - 17:30  DRIVING (5 sati)              → Drive: 00:00 ostalo

17:30 - Iskoristio svih 11h, ALI nastavlja da vozi...

17:30 - 18:30  DRIVING (1 sat)               → ⚠️ VIOLATION!

❌ DRIVING_11_HOUR Violation
   Početak: 17:30
   Trajanje: 1 sat
   Prekoračenje: 1 sat preko limita
```

### Šta NE Resetuje 11-Hour Timer

- Kraće pauze (manje od 10h)
- ON DUTY vreme
- Sleeper berth kraći od 10h

### Šta RESETUJE 11-Hour Timer

- 10 uzastopnih sati OFF DUTY
- 10 uzastopnih sati SLEEPER BERTH
- Kombinacija (7h SB + 3h OFF = 10h ukupno uzastopno)

---

## 3. 14-Hour Shift Limit

### Pravilo
> Vozač ne sme voziti nakon **14 sati od početka rada**, bez obzira na pauze.

### Ključna Razlika od 11h Limita
- **11h limit:** Računa samo DRIVING vreme
- **14h limit:** Računa UKUPNO vreme od početka smene (uključujući pauze!)

### Kako Funkcioniše

```
┌─────────────────────────────────────────────────────────────────┐
│                    14-HOUR SHIFT LIMIT                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Početak smene: 06:00                                           │
│  Kraj prozora za vožnju: 20:00 (14h kasnije)                    │
│                                                                  │
│  06:00                                              20:00       │
│  ├───────────────────────────────────────────────────┤          │
│  │◄──────────── 14-Hour Window ────────────────────►│          │
│  │                                                   │          │
│  │  MOŽEŠ VOZITI                                     │ NE MOŽEŠ │
│  │  (dok imaš 11h drive time)                        │ VOZITI   │
│  │                                                   │          │
│  └───────────────────────────────────────────────────┘          │
│                                                                  │
│  ⚠️ PAUZE NE PRODUŽAVAJU OVAJ PROZOR!                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer 1: 14h Prozor Ističe Pre 11h Vožnje

```
06:00 - Početak smene (14h window do 20:00)

06:00 - 06:30  ON DUTY (pregled)             → Shift: 13:30 ostalo
06:30 - 10:30  DRIVING (4h)                  → Shift: 09:30 ostalo | Drive: 07:00 ostalo
10:30 - 12:00  ON DUTY (utovar, 1.5h)        → Shift: 08:00 ostalo | Drive: 07:00 ostalo
12:00 - 13:00  OFF DUTY (ručak, 1h)          → Shift: 07:00 ostalo | Drive: 07:00 ostalo
                                               ⚠️ PAUZA NE PRODUŽAVA 14h!
13:00 - 17:00  DRIVING (4h)                  → Shift: 03:00 ostalo | Drive: 03:00 ostalo
17:00 - 18:00  ON DUTY (istovar)             → Shift: 02:00 ostalo | Drive: 03:00 ostalo
18:00 - 20:00  DRIVING (2h)                  → Shift: 00:00 ostalo | Drive: 01:00 ostalo

20:00 - MORA DA STANE!
        Ima još 1h drive time, ALI 14h prozor je istekao.
```

### Primer 2: Violation - Vožnja Nakon 14h

```
06:00 - Početak smene

06:00 - 14:00  DRIVING (8h)                  → Shift: 06:00 ostalo | Drive: 03:00 ostalo
14:00 - 16:00  ON DUTY (čekanje na rampi)    → Shift: 04:00 ostalo | Drive: 03:00 ostalo
16:00 - 17:00  OFF DUTY (pauza)              → Shift: 03:00 ostalo (i dalje teče!)
17:00 - 20:00  DRIVING (3h)                  → Shift: 00:00 ostalo | Drive: 00:00 ostalo

20:00 - 14h prozor istekao, ALI nastavlja...

20:00 - 21:00  DRIVING (1h)                  → ⚠️ VIOLATION!

❌ SHIFT_14_HOUR Violation
   Početak: 20:00
   Trajanje: 1 sat
   Problem: Vožnja nakon isteka 14h prozora
```

### Važno: Pauze Ne Pauziraju 14h Sat

```
Početak: 06:00
Pauza:   12:00 - 14:00 (2h OFF DUTY)

Iako je vozač bio OFF DUTY 2 sata, 14h window i dalje ističe u 20:00!
Pauza NE DODAJE 2 sata na kraj.
```

---

## 4. 30-Minute Break Rule

### Pravilo
> Vozač mora uzeti pauzu od **minimum 30 minuta** pre nego što odvozi **8 sati kumulativno**.

### Kako Funkcioniše

```
┌─────────────────────────────────────────────────────────────────┐
│                    30-MINUTE BREAK RULE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  0h              4h              8h                              │
│  ├───────────────┼───────────────┤                              │
│  │◄─────── Driving Time ────────►│                              │
│  │                               │                               │
│  │    MORAŠ UZETI 30min PAUZU   │                               │
│  │    PRE NEGO ŠTO DOĐEŠ OVDE   │                               │
│  │                               │                               │
│  └───────────────────────────────┘                              │
│                                                                  │
│  Validne pauze: OFF DUTY ili SLEEPER BERTH (min 30 min)         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer 1: Ispravno Korišćenje Pauze

```
06:00 - 10:00  DRIVING (4h)                  → Break timer: 4:00
10:00 - 10:30  OFF DUTY (30min pauza)        → Break timer: RESET ✓
10:30 - 14:30  DRIVING (4h)                  → Break timer: 4:00
14:30 - 15:00  OFF DUTY (30min pauza)        → Break timer: RESET ✓
15:00 - 19:00  DRIVING (4h)                  → Break timer: 4:00

Vozač je vozio 12h ukupno, ali nikad više od 8h bez pauze. ✓
```

### Primer 2: Violation - Bez Pauze

```
06:00 - 10:00  DRIVING (4h)                  → Break timer: 4:00
10:00 - 10:15  ON DUTY (kratka pauza)        → Break timer: 4:00 (ON DUTY ne resetuje!)
10:15 - 14:15  DRIVING (4h)                  → Break timer: 8:00 ⚠️

14:15 - VIOLATION! 8h vožnje bez 30min pauze.

❌ BREAK_30_MIN Violation
   Početak: 14:15
   Problem: Prekoračeno 8h vožnje bez pauze
   Rešenje: Mora odmah uzeti 30min OFF DUTY
```

### Primer 3: Pauza Koja Ne Važi

```
06:00 - 13:00  DRIVING (7h)                  → Break timer: 7:00
13:00 - 13:20  OFF DUTY (20min)              → Break timer: 7:00 (nije 30min!)
13:20 - 14:20  DRIVING (1h)                  → Break timer: 8:00 ⚠️

❌ VIOLATION! Pauza od 20min nije dovoljna.
```

### Šta Resetuje Break Timer

| Aktivnost | Resetuje? | Objašnjenje |
|-----------|-----------|-------------|
| 30+ min OFF DUTY | ✓ DA | Standardna pauza |
| 30+ min SLEEPER BERTH | ✓ DA | Spavanje u kabini |
| 20 min OFF DUTY | ✗ NE | Prekratko |
| 30+ min ON DUTY | ✗ NE | Nije odmor |
| Kombinacija 15min OFF + 15min SB | ✓ DA | Uzastopno 30min |

---

## 5. 60/70-Hour Cycle Limit

### Pravilo
> Vozač ne sme biti ON DUTY više od **60 sati u 7 dana** ili **70 sati u 8 dana**.

### Dva Tipa Ciklusa

| Ciklus | Limit | Period | Koristi se kada |
|--------|-------|--------|-----------------|
| **60/7** | 60 sati | 7 dana | Kompanija NE radi svaki dan |
| **70/8** | 70 sati | 8 dana | Kompanija radi svaki dan |

### Kako Funkcioniše "Rolling" Period

```
┌─────────────────────────────────────────────────────────────────┐
│                    70-HOUR / 8-DAY CYCLE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Danas je PETAK. Gledamo poslednjih 8 dana:                     │
│                                                                  │
│  Subota (pre 7 dana):   10h ON DUTY                             │
│  Nedelja (pre 6 dana):   8h ON DUTY                             │
│  Ponedeljak (pre 5):    12h ON DUTY                             │
│  Utorak (pre 4):        11h ON DUTY                             │
│  Sreda (pre 3):         10h ON DUTY                             │
│  Četvrtak (pre 2):       9h ON DUTY                             │
│  Petak (pre 1):          5h ON DUTY                             │
│  DANAS (Subota):         ?h ON DUTY                             │
│  ─────────────────────────────────────                          │
│  Ukupno:                65h                                      │
│                                                                  │
│  Dostupno DANAS: 70 - 65 = 5h                                   │
│                                                                  │
│  ⚠️ SUTRA (nova Nedelja) se Subota od pre 7 dana BRIŠE         │
│     jer izlazi iz 8-dnevnog prozora!                            │
│     Novo dostupno: 70 - 55 = 15h                                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer: Praćenje Cycle Vremena

```
Dan 1 (Ponedeljak):  11h ON DUTY → Cycle: 11/70
Dan 2 (Utorak):      10h ON DUTY → Cycle: 21/70
Dan 3 (Sreda):       11h ON DUTY → Cycle: 32/70
Dan 4 (Četvrtak):    10h ON DUTY → Cycle: 42/70
Dan 5 (Petak):       11h ON DUTY → Cycle: 53/70
Dan 6 (Subota):      10h ON DUTY → Cycle: 63/70
Dan 7 (Nedelja):      7h ON DUTY → Cycle: 70/70 ← LIMIT!

Dan 8 (Ponedeljak):
  - Dan 1 (prošli ponedeljak) izlazi iz prozora
  - Oslobađa se: 11h
  - Novo dostupno: 70 - 59 = 11h
```

### Primer: Violation

```
Cycle status: 68h/70h (2h ostalo)

08:00 - 10:00  DRIVING (2h)                  → Cycle: 70/70
10:00 - 10:30  ON DUTY (30min)               → Cycle: 70.5/70 ⚠️

❌ CYCLE_70_HOUR_8_DAY Violation
   Početak: 10:00
   Prekoračenje: 30 minuta
   Problem: Prekoračen 70h limit u 8 dana
```

---

## 6. 34-Hour Restart

### Pravilo
> Vozač može resetovati Cycle timer uzimanjem **34 uzastopna sata OFF DUTY**.

### Kako Funkcioniše

```
┌─────────────────────────────────────────────────────────────────┐
│                    34-HOUR RESTART                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  SITUACIJA: Cycle je 70/70 (iscrpljen)                          │
│                                                                  │
│  OPCIJA 1: Čekaj da dani "ispadnu" iz 8-dnevnog prozora         │
│            (sporo, dobija se malo sati dnevno)                  │
│                                                                  │
│  OPCIJA 2: Uzmi 34h OFF DUTY = POTPUNI RESET!                   │
│                                                                  │
│  ════════════════════════════════════════════                   │
│                                                                  │
│  Subota 18:00 - Počinje 34h restart                             │
│  │                                                               │
│  │  34 sata OFF DUTY ili SLEEPER BERTH                          │
│  │                                                               │
│  ▼                                                               │
│  Nedelja 04:00 - Završava 34h restart                           │
│                                                                  │
│  ✓ CYCLE RESET: Sada ima 70h ponovo!                            │
│  ✓ 11h DRIVE RESET                                              │
│  ✓ 14h SHIFT RESET                                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer: 34-Hour Restart

```
Petak 20:00 - Cycle: 70/70, mora da stane
Petak 20:00 - Počinje OFF DUTY

... 34 sata kasnije ...

Nedelja 06:00 - Završeno 34h OFF DUTY

✓ Cycle RESET: 0/70 (70h dostupno)
✓ Drive RESET: 0/11 (11h dostupno)
✓ Shift RESET: 0/14 (14h dostupno)
✓ Break RESET

Vozač može ponovo raditi punu nedelju!
```

### Važno: Šta Prekida 34h Restart

| Aktivnost | Prekida? |
|-----------|----------|
| Bilo kakav ON DUTY | ✓ DA - mora početi ispočetka |
| Bilo kakav DRIVING | ✓ DA - mora početi ispočetka |
| Prelazak iz OFF u SB | ✗ NE - nastavlja se |
| Prelazak iz SB u OFF | ✗ NE - nastavlja se |

---

## 7. Sleeper Berth Provisions

### Osnovno Pravilo
> Sleeper Berth (SB) se računa kao OFF DUTY za potrebe odmora.

### Split Sleeper Berth (Podeljeno Spavanje)

Vozač može podeliti 10h odmor na dva dela:
- **7h u Sleeper Berth** (mora biti uzastopno)
- **3h OFF DUTY ili SB** (može biti odvojeno)

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPLIT SLEEPER BERTH                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  STANDARDNO: 10h uzastopno OFF/SB                               │
│                                                                  │
│  SPLIT OPCIJA:                                                  │
│  ┌─────────────────────────────────────────────────┐            │
│  │  7h SB  │  ...vožnja/rad...  │  3h OFF/SB  │               │
│  └─────────────────────────────────────────────────┘            │
│                                                                  │
│  ILI:                                                           │
│  ┌─────────────────────────────────────────────────┐            │
│  │  3h OFF  │  ...vožnja/rad...  │  7h SB  │                  │
│  └─────────────────────────────────────────────────┘            │
│                                                                  │
│  ⚠️ 7h deo MORA biti u Sleeper Berth                           │
│  ⚠️ Ni jedan deo ne sme biti kraći od navedenog                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Primer: Split Sleeper

```
06:00 - 10:00  DRIVING (4h)
10:00 - 13:00  SLEEPER BERTH (3h) ← Prvi deo
13:00 - 17:00  DRIVING (4h)
17:00 - 18:00  ON DUTY (1h)
18:00 - 21:00  DRIVING (3h)
21:00 - 04:00  SLEEPER BERTH (7h) ← Drugi deo

04:00 - Završen split (3h + 7h = 10h)
        ✓ RESET svih timera
```

---

## 8. Personal Conveyance

### Definicija
> Korišćenje CMV vozila za **lične potrebe** dok je vozač OFF DUTY.

### Kada Je Dozvoljeno

| Dozvoljeno | Nije Dozvoljeno |
|------------|-----------------|
| ✓ Vožnja do restorana | ✗ Nastavak ka destinaciji tereta |
| ✓ Vožnja do motela | ✗ Vožnja ka sledećem utovaru |
| ✓ Pomeranje na sigurnije mesto | ✗ Bilo kakav plaćeni rad |
| ✓ Vožnja kući (prazan kamion) | ✗ Vožnja sa teretom ka isporuci |

### Kako Se Beleži

```
Status: PERSONAL_CONVEYANCE (podtip OFF DUTY)

Primer:
18:00 - Vozač završava dan, parkira na truck stop-u
18:00 - 18:30  PERSONAL CONVEYANCE (vožnja do restorana, 5 milja)
18:30 - 19:30  OFF DUTY (večera)
19:30 - 20:00  PERSONAL CONVEYANCE (povratak do kamiona)

⚠️ PC vreme NE TROŠI HOS timere
⚠️ Ali se MORA beležiti u ELD
```

---

## 9. Yard Move

### Definicija
> Pomeranje CMV vozila unutar **dvorišta terminala** dok je vozač ON DUTY.

### Karakteristike

| Aspekt | Detalj |
|--------|--------|
| Status | ON_DUTY (ne DRIVING) |
| Utiče na | 14h Shift, 60/70h Cycle |
| NE utiče na | 11h Driving |
| Gde | Samo unutar dvorišta/terminala |

### Primer

```
06:00 - Vozač dolazi na terminal
06:00 - 06:30  ON DUTY (pregled vozila)
06:30 - 07:00  YARD MOVE (pomeranje kamiona na dock)
07:00 - 08:00  ON DUTY (utovar)
08:00 - 12:00  DRIVING (vožnja na autoputu)

Yard Move NE troši 11h driving, ALI troši 14h shift.

Stanje u 12:00:
- Drive: 7:00 ostalo (samo 4h vožnje)
- Shift: 8:00 ostalo (6h ukupno od početka)
```

---

## 10. Violations - Tipovi i Primeri

### 10.1 DRIVING_11_HOUR

**Opis:** Vožnja preko 11 sati u jednoj smeni.

```
PRIMER VIOLATION-a:

Vozač je vozio 11h i nastavlja...

Timeline:
06:00 - Početak smene
06:00 - 17:00 - 11h DRIVING (sa pauzama)
17:00 - Iskoristio 11h drive time

17:00 - 18:00 - DRIVING (1h) ← VIOLATION!

Violation Record:
┌────────────────────────────────────────┐
│ Type: DRIVING_11_HOUR                  │
│ Start: 17:00                           │
│ End: 18:00 (ili kada prestane)         │
│ Duration: 60 minutes                   │
│ Exceeded by: 60 minutes                │
│ Severity: HIGH                         │
└────────────────────────────────────────┘
```

### 10.2 SHIFT_14_HOUR

**Opis:** Vožnja nakon 14 sati od početka smene.

```
PRIMER VIOLATION-a:

Timeline:
06:00 - Početak smene (14h window do 20:00)
06:00 - 18:00 - Rad i vožnja (sa pauzama)
18:00 - 20:00 - Još 2h vožnje
20:00 - 14h window ISTEKAO

20:00 - 21:00 - DRIVING ← VIOLATION!

Violation Record:
┌────────────────────────────────────────┐
│ Type: SHIFT_14_HOUR                    │
│ Start: 20:00                           │
│ End: 21:00                             │
│ Duration: 60 minutes                   │
│ Problem: Driving after 14h window      │
│ Severity: HIGH                         │
└────────────────────────────────────────┘
```

### 10.3 BREAK_30_MIN

**Opis:** Vožnja preko 8 sati bez 30-minutne pauze.

```
PRIMER VIOLATION-a:

Timeline:
06:00 - 10:00 - DRIVING (4h)
10:00 - 10:15 - ON DUTY (15min - NIJE pauza!)
10:15 - 14:15 - DRIVING (4h)
14:15 - 8h vožnje bez pauze ← VIOLATION POČINJE!

14:15 - 15:00 - DRIVING (45min, nastavlja) ← VIOLATION TRAJE

15:00 - 15:30 - OFF DUTY (30min pauza)
15:30 - Violation ZAVRŠAVA (uzeo pauzu)

Violation Record:
┌────────────────────────────────────────┐
│ Type: BREAK_30_MIN                     │
│ Start: 14:15                           │
│ End: 15:00                             │
│ Duration: 45 minutes                   │
│ Drove without break: 8h 45min          │
│ Severity: MEDIUM                       │
└────────────────────────────────────────┘
```

### 10.4 CYCLE_70_HOUR_8_DAY

**Opis:** ON DUTY preko 70 sati u 8 dana.

```
PRIMER VIOLATION-a:

Poslednjih 7 dana: 65h ON DUTY
Danas do sada: 5h ON DUTY
Cycle: 70/70 (na limitu)

Nastavlja rad...

10:00 - 10:30 - ON DUTY (30min) ← VIOLATION!

Violation Record:
┌────────────────────────────────────────┐
│ Type: CYCLE_70_HOUR_8_DAY              │
│ Start: 10:00                           │
│ End: (ongoing)                         │
│ Exceeded by: 30 minutes                │
│ 8-day total: 70h 30min                 │
│ Severity: HIGH                         │
└────────────────────────────────────────┘
```

### 10.5 FORM_AND_MANNER

**Opis:** Nepravilno vođenje evidencije.

```
PRIMERI:

1. Nedostaje lokacija za event
   ┌────────────────────────────────────────┐
   │ Event: DRIVING started at 08:00        │
   │ Location: (missing)                    │
   │ Problem: FMCSA zahteva lokaciju        │
   └────────────────────────────────────────┘

2. Praznina u logu
   ┌────────────────────────────────────────┐
   │ 08:00 - 12:00: DRIVING                 │
   │ 12:00 - 14:00: (no status recorded)    │
   │ 14:00 - 18:00: DRIVING                 │
   │ Problem: 2h bez statusa                │
   └────────────────────────────────────────┘

3. Preklapanje eventa
   ┌────────────────────────────────────────┐
   │ 08:00 - 12:00: DRIVING                 │
   │ 11:00 - 14:00: ON DUTY                 │
   │ Problem: Events se preklapaju          │
   └────────────────────────────────────────┘
```

### Tabela Svih Violation Tipova

| Tip | CFR | Ozbiljnost | Automatska Detekcija |
|-----|-----|------------|---------------------|
| DRIVING_11_HOUR | 395.3(a)(3)(i) | Visoka | ✓ Da |
| SHIFT_14_HOUR | 395.3(a)(2) | Visoka | ✓ Da |
| BREAK_30_MIN | 395.3(a)(3)(ii) | Srednja | ✓ Da |
| CYCLE_60_HOUR_7_DAY | 395.3(b)(1) | Visoka | ✓ Da |
| CYCLE_70_HOUR_8_DAY | 395.3(b)(2) | Visoka | ✓ Da |
| FORM_AND_MANNER | 395.8 | Niska | ✓ Da |
| FALSE_LOG | 395.8(e) | Veoma Visoka | ⚠️ Delimično |

---

## 11. Praktični Primeri Scenarija

### Scenario 1: Perfektan Dan

```
═══════════════════════════════════════════════════════════════
PERFEKTAN DAN - Bez Violation-a
═══════════════════════════════════════════════════════════════

Prethodni dan: 10h OFF DUTY (20:00 - 06:00)
Početno stanje: Drive 11:00 | Shift 14:00 | Break 8:00 | Cycle 55:00

06:00 │ ON DUTY - Pregled vozila (15min)
      │ Drive: 11:00 | Shift: 13:45 | Cycle: 54:45
      │
06:15 │ DRIVING - Polazak
      │
10:15 │ DRIVING - 4h vožnje (pauza za gorivo)
      │ Drive: 07:00 | Shift: 09:45 | Break: 4:00 | Cycle: 50:45
      │
10:15 │ OFF DUTY - Pauza 30min (gorivo + kafa)
      │
10:45 │ Break timer RESET ✓
      │
10:45 │ DRIVING - Nastavak
      │
14:45 │ DRIVING - Još 4h (dolazak na istovar)
      │ Drive: 03:00 | Shift: 05:45 | Break: 4:00 | Cycle: 46:45
      │
14:45 │ ON DUTY - Istovar (1h)
      │
15:45 │ Drive: 03:00 | Shift: 04:45 | Cycle: 45:45
      │
15:45 │ OFF DUTY - Pauza 30min
      │
16:15 │ Break timer RESET ✓
      │
16:15 │ DRIVING - Vožnja do parkinga
      │
18:15 │ DRIVING - 2h vožnje (kraj dana)
      │ Drive: 01:00 | Shift: 02:45 | Cycle: 43:45
      │
18:15 │ OFF DUTY - Kraj smene
      │

REZULTAT:
✓ Vozio: 10h (od 11h max)
✓ Smena: 12h 15min (od 14h max)
✓ Pauze: Dve pauze od 30min
✓ Cycle: 43:45 ostalo
✓ Nema violation-a!
```

### Scenario 2: Dan Sa Violation-ima

```
═══════════════════════════════════════════════════════════════
PROBLEMATIČAN DAN - Multiple Violations
═══════════════════════════════════════════════════════════════

Prethodni dan: Samo 8h OFF DUTY (problem!)
Početno stanje: Drive 11:00 | Shift 14:00 | Break 8:00 | Cycle 68:00

⚠️ PROBLEM 1: Nije imao 10h odmor - timeri NISU resetovani!
   Zapravo nastavlja prethodnu smenu.

06:00 │ ON DUTY - Pregled vozila
      │ Shift timer nastavlja od prethodnog dana!
      │
06:15 │ DRIVING
      │
14:15 │ DRIVING - 8h vožnje BEZ PAUZE
      │ ❌ BREAK_30_MIN VIOLATION POČINJE!
      │
14:15 │ Nastavlja da vozi...
      │
15:00 │ DRIVING - Još 45min vožnje
      │ Break violation: 45min
      │
15:00 │ OFF DUTY - Konačno pauza (30min)
      │ ✓ Break violation ZAVRŠAVA
      │
15:30 │ DRIVING - Nastavlja
      │ Cycle: 68 + 9.5h = 77.5h
      │ ❌ CYCLE_70_HOUR Violation od 15:30!
      │
17:00 │ DRIVING
      │ Cycle violation: 1.5h
      │
17:00 │ Shvata problem, staje.

REZULTAT:
❌ BREAK_30_MIN: 14:15 - 15:00 (45min)
❌ CYCLE_70_HOUR: 15:30 - 17:00 (1.5h)
⚠️ Mogući FORM_AND_MANNER ako nedostaju podaci
```

### Scenario 3: Split Sleeper Berth

```
═══════════════════════════════════════════════════════════════
SPLIT SLEEPER - Napredna Tehnika
═══════════════════════════════════════════════════════════════

Cilj: Maksimizovati produktivnost koristeći split sleep

DAY 1:
06:00 │ Početak smene (nakon 10h odmor)
      │ Drive: 11:00 | Shift: 14:00
      │
06:00 │ DRIVING
      │
13:00 │ 7h vožnje - Umoran, odlučuje za split
      │ Drive: 04:00 | Shift: 07:00
      │
13:00 │ SLEEPER BERTH - 7h spavanje
      │
20:00 │ Buđenje (7h SB završeno - PRVI DEO SPLIT-a)
      │ ⚠️ Timeri "pauzirani" - nije reset
      │
20:00 │ DRIVING
      │
23:00 │ 3h vožnje
      │ Drive: 01:00 | Shift: 04:00
      │
23:00 │ SLEEPER BERTH - 3h (DRUGI DEO SPLIT-a)
      │
DAY 2:
02:00 │ Buđenje
      │ ✓ SPLIT COMPLETE: 7h + 3h = 10h
      │ ✓ Drive RESET: 11:00
      │ ✓ Shift RESET: 14:00
      │
02:00 │ Nova smena počinje!

PREDNOST SPLIT-a:
- Umesto 10h uzastopno OFF, vozač je "podelio" odmor
- Mogao je voziti još 3h između delova spavanja
- Maksimizovao produktivnost
```

---

## Dodatak: Quick Reference Card

```
╔═══════════════════════════════════════════════════════════════╗
║                 HOS QUICK REFERENCE CARD                       ║
╠═══════════════════════════════════════════════════════════════╣
║                                                                 ║
║  DRIVING LIMIT:     11 hours max                               ║
║  SHIFT WINDOW:      14 hours (then MUST stop)                  ║
║  BREAK REQUIRED:    30 min before 8h driving                   ║
║  CYCLE LIMIT:       70 hours / 8 days                          ║
║  RESTART:           34 hours OFF = full reset                  ║
║                                                                 ║
╠═══════════════════════════════════════════════════════════════╣
║                                                                 ║
║  TO RESET ALL TIMERS:                                          ║
║  • 10 consecutive hours OFF DUTY or SLEEPER BERTH              ║
║                                                                 ║
║  TO RESET CYCLE:                                               ║
║  • 34 consecutive hours OFF DUTY                               ║
║                                                                 ║
║  TO RESET BREAK TIMER:                                         ║
║  • 30 minutes OFF DUTY or SLEEPER BERTH                        ║
║                                                                 ║
╠═══════════════════════════════════════════════════════════════╣
║                                                                 ║
║  ⚠️ COMMON MISTAKES:                                           ║
║  • Thinking breaks extend 14h window (they don't!)             ║
║  • ON DUTY counts as break (it doesn't!)                       ║
║  • Forgetting 30-min break rule                                ║
║  • Not tracking cycle hours                                    ║
║                                                                 ║
╚═══════════════════════════════════════════════════════════════╝
```

---

## Kako Aplikacija Računa HOS

### Algoritam (Pojednostavljeno)

```
1. UČITAJ sve evente za poslednjih 8 dana

2. SORTIRAJ hronološki

3. PRONAĐI poslednji 10h+ odmor (početak smene)

4. OD TOG MOMENTA RAČUNAJ:
   - DRIVE TIME = suma svih DRIVING perioda
   - SHIFT TIME = vreme od početka smene do sad
   - BREAK TIME = vreme od poslednje 30min pauze

5. ZA CYCLE:
   - Uzmi sve dane u 8-dnevnom prozoru
   - Saberi svo ON DUTY vreme (DRIVING + ON_DUTY)

6. PROVERI VIOLATIONS:
   - Ako DRIVE > 11h → VIOLATION
   - Ako SHIFT > 14h i još vozi → VIOLATION
   - Ako BREAK > 8h bez pauze → VIOLATION
   - Ako CYCLE > 70h → VIOLATION

7. AŽURIRAJ UI svakog minuta
```

### Primer Kalkulacije

```
Događaji danas:
06:00 - DRIVING started (nakon 10h OFF)
10:00 - ON_DUTY started
10:30 - OFF_DUTY started (pauza)
11:00 - DRIVING started
15:00 - Trenutno vreme

KALKULACIJA:
┌────────────────────────────────────────────────────────┐
│ Drive Time:                                            │
│   06:00-10:00 = 4h                                     │
│   11:00-15:00 = 4h                                     │
│   TOTAL = 8h                                           │
│   REMAINING = 11h - 8h = 3h                            │
├────────────────────────────────────────────────────────┤
│ Shift Time:                                            │
│   06:00 do 15:00 = 9h                                  │
│   REMAINING = 14h - 9h = 5h                            │
├────────────────────────────────────────────────────────┤
│ Break Time:                                            │
│   Poslednja pauza: 10:30-11:00 (30min) ✓               │
│   Od tada: 4h vožnje                                   │
│   REMAINING = 8h - 4h = 4h                             │
├────────────────────────────────────────────────────────┤
│ Violations: NONE ✓                                     │
└────────────────────────────────────────────────────────┘
```

---

> **Dokument kreiran:** Decembar 2024
> **Regulativa:** FMCSA 49 CFR Part 395
> **Verzija pravila:** 2023 (sa September 2020 amendments)
