# MASTER_PRODUCT_SPECIFICATION
**Personal Tracker (Work + Trading + Plan Compliance)**
**Status**: LOCKED PRODUCTION BASELINE
**Architecture**: Offline-First Jetpack Compose + Room SQLite

---

## 1. PRODUCT SCOPE & NON-GOALS

The application consists exclusively of the following seven modules:
1. **WORK** (Daily timer and manual work hour tracking)
2. **TRADING** (Trade journal with entry, exit, and manual P&L)
3. **PLAN COMPLIANCE** (Plan followed flag and 7 predefined rule violation tracking)
4. **DAILY** (Single-day unified view and data entry)
5. **3-DAY REVIEWS** (Fixed calendar-based 3-day performance blocks)
6. **OVERALL REVIEW** (Cumulative period review directly aggregating raw records)
7. **DASHBOARD** (Glance-level summary of overall, current work, and latest 3-day review)

### Absolute Non-Goals (Scope Fence)
Under NO circumstances may any future AI or developer introduce:
- **Expenses / Expense tracking / Expense categories** (Expenses are permanently OUTSIDE scope)
- **Productivity scores, discipline scores, or composite rating algorithms**
- **Win rate, ROI, Sharpe ratio, or complex risk metrics**
- **Trade attributes beyond the contract** (No: Instrument, Buy/Sell, Quantity, Trade Time, Notes, Broker, Strategy)
- **Session-history analytics or break timers**
- **Cloud accounts, login, remote databases, external APIs, or background sync**
- **Gamification, streaks, or notifications**

---

## 2. WORK CONTRACT

### User-Facing Capabilities
- Actual Work Time (formatted as `HHh MMm` or `HH:MM:SS` while running)
- 8 Hours Completed indicator (`YES` / `NO`)
- Timer controls: `START`, `PAUSE`, `RESUME`, `STOP`
- Manual Work Time correction dialog

### Business Rules & Invariants
- **Active Time Accumulation**: Work time increments ONLY while in `WORKING` state.
- **Pause State**: In `PAUSED` state, time accumulation freezes completely. Paused duration is excluded.
- **Storage**: Persisted as raw integer minutes (`workTimeMinutes`) in Room SQLite.
- **8-Hour Threshold**:
  - Exactly 480 minutes or more $\rightarrow$ `8 Hours Completed = YES`
  - Less than 480 minutes (e.g. 479 min) $\rightarrow$ `8 Hours Completed = NO`
  - The actual time recorded must remain exact even above 8 hours (e.g. 552 min).
- **Interruption Recovery**: Timer state is computed from `activeStartTimestamp` and `accumulatedSeconds` in Room, surviving app backgrounding, screen locks, and process recreation.
- **Midnight Boundary**: When an active timer crosses midnight, active duration up to `23:59:59.999` is committed to the prior date, transitioning it to `STOPPED`. The new calendar day starts at 0 minutes in `STOPPED` state until explicitly started.
- **Date Protection**: Future calendar dates CANNOT initiate work or create work records. Historical work time can be manually corrected.

---

## 3. TRADING CONTRACT

Every trade entity contains exactly five user-facing primary attributes:
1. **ENTRY**: Required numeric value (Double)
2. **EXIT**: Required numeric value (Double)
3. **P&L**: Required numeric value (Double, manual input). Supports positive, negative, and zero values.
4. **PLAN FOLLOWED**: Required boolean selection (`YES` / `NO`)
5. **RULES VIOLATED**: Required boolean selection (`YES` / `NO`)

### Business Rules & Invariants
- **Manual P&L Integrity**: P&L is **never** automatically calculated from Entry and Exit. It represents the user's actual realized profit/loss.
- **Plan & Rule Independence**: `Plan Followed` and `Rules Violated` are completely independent fields. All 4 combinations are valid:
  - Plan Followed = YES, Rules Violated = YES
  - Plan Followed = YES, Rules Violated = NO
  - Plan Followed = NO, Rules Violated = YES
  - Plan Followed = NO, Rules Violated = NO
- **Rule Selection Enforcement**:
  - When `Rules Violated = YES`, at least one of the 7 predefined rules must be selected. Saving with 0 selected rules is strictly blocked.
  - When `Rules Violated = NO`, rule selection is bypassed and stored as an empty list.
- **The Seven Predefined Rules**:
  1. `Entry condition satisfied`
  2. `Position size within limit`
  3. `Stop-loss used`
  4. `No revenge trade`
  5. `No overtrading`
  6. `Entry according to setup`
  7. `Exit according to predefined rule`

---

## 4. DAILY CONTRACT

All Daily metrics must be derived dynamically from raw `work` and `trades` records:
- **Work Section**: Displays `Work Time` and `8 Hours Completed (YES/NO)`.
- **Trading Section**: Displays `Trades` count, `Total P&L` (formatted with `+₹` / `-₹` / `₹0`), trade list, and `Add Trade` action.
- **Plan Compliance Section**:
  - `Plan Followed`: Formatted as `X / Y` (e.g., `3 / 4`)
  - `Violation Trades`: Formatted as `X / Y` (e.g., `1 / 4`)
  - `Rule Violations`: Total count of individual rule violations across trades
  - `Rule Breakdown`: Individual violation counts for each of the 7 rules
- **Zero-Trade Day Display**:
  - `Trades = 0`
  - `Total P&L = ₹0`
  - `Plan Followed = —`
  - `Violation Trades = —`
  - `Rule Violations = 0`
  - *No artificial `0/0` or percentage strings.*

---

## 5. 3-DAY REVIEW CONTRACT

- **Fixed Calendar Blocks**: Blocks are strictly anchored to the calendar month (1–3, 4–6, 7–9, 10–12, 13–15, 16–18, 19–21, 22–24, 25–27, 28–30, and crossing into the next month). They are NOT rolling 72-hour windows.
- **Calendar Inclusivity**: A day with 0 work and 0 trades counts as a full calendar day.
- **Completion Requirement**: A block is designated an official completed review **only after all three calendar days have completed**. In-progress blocks are not listed in Review History.
- **Metrics**: Total Work Time, 8h Completed (X/Y days), Trades, Total P&L, Plan Followed (X/Y trades), Violation Trades (X/Y trades), and Rule Violations.

---

## 6. OVERALL REVIEW CONTRACT

- **Accumulated Scope**: Represents the accumulated tracking period from the earliest recorded date to today.
- **No Monthly Reset**: Does NOT automatically reset at month boundaries.
- **Raw Data Aggregation**: Overall metrics must aggregate raw `work` and `trades` records directly. It must **never** average previous 3-day review percentages or summaries.

---

## 7. DASHBOARD CONTRACT

The Dashboard is a glance-level executive screen containing:
1. **OVERALL**: Current accumulated period metrics (Total Work, 8h Done X/Y days, Trades, Total P&L, Plan Followed X/Y, Violation Trades X/Y, Rule Violations).
2. **CURRENT WORK**: Today's active work time and 8-hour completion status.
3. **LAST 3 DAYS**: Summary of the most recently completed 3-day review block.
4. **ACTION**: Direct navigation to Daily and Overall views.

---

## 8. SOURCE-OF-TRUTH & DATABASE CONTRACT

### Authoritative Tables Only
- `work`: Primary source of truth for work tracking (`date` PK, `workTimeMinutes`, timer metadata).
- `trades`: Primary source of truth for trades and compliance (`tradeId` PK, `date`, `entry`, `exit`, `pnl`, `planFollowed`, `rulesViolated`, `violatedRuleIds`).
- `rules`: Primary source of truth for rule definitions (`ruleId` PK, `ruleName`).

### Architectural Invariant
- **No Derived Tables**: There shall be NO authoritative `dashboard_metrics`, `daily_metrics`, or `review_metrics` tables.
- All derived calculations are computed in memory via dedicated domain calculators (`DailyCalculator`, `ReviewCalculator`, `TradeCalculator`, `WorkCalculator`) fed by Room `Flow` streams.
- Any edit or deletion of a raw record propagates reactively to all screens.

---

## 9. OFFLINE & PRIVACY CONTRACT

- **Zero Network Permissions**: The app must declare zero internet permissions in `AndroidManifest.xml`.
- **Local Storage**: All data resides strictly in SQLite via Room on local device storage.
- **No Analytics / Telemetry**: No third-party SDKs, tracking libraries, or external logging.

---

## 10. GOVERNANCE & ANTI-DRIFT RULES

1. **Conflict Resolution**: If any future prompt or user request conflicts with this baseline, the AI agent MUST STOP and explicitly identify the conflict. It must not silently overwrite or dilute this baseline.
2. **Anti-Feature-Creep**: Do not add charts, scores, percentages, notifications, or new fields merely because they exist in other apps.
3. **Safe Migrations**: If schema modification is ever requested by the product owner, a safe Room migration must be written to preserve existing user data. Destructive drops are prohibited in production.
4. **Mandatory Regression Suite**: Any future functional change must run and pass `FinalAcceptanceVerificationTest`, `FullQaSpecificationTest`, `DatabaseFoundationTest`, and `ReviewSystemTest`.
