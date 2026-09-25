# 🚖 MoveInSync — Smart Employee Transportation & Cab Pooling System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2F18-blue.svg)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-19-cyan.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue.svg)](https://www.typescriptlang.org/)
[![Tests](https://img.shields.io/badge/Integration%20Tests-93%2F93%20Passing-success.svg)]()
[![License](https://img.shields.io/badge/License-MIT-purple.svg)]()

> **LPU Backend Case Studies 2026 — Official Reference Implementation**  
> *Case Study 1: Employee Cab Pooling & Smart Pickup Routing Engine*

---

## 📑 Table of Contents
1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
   - [I. Spatial Clustering & Strict Seat Limits](#i-grouping-nearby-employees-into-cabs)
   - [II. Best Pickup Order & Routing Engine (2-Opt TSP & Max-Ride)](#ii-best-pickup-order-routing-engine)
   - [III. Night Safety Rules & Automated Escort Provisioning](#iii-night-safety-rules)
   - [IV. Handling Last-Minute Changes (Cancellations & Late Bookings)](#iv-handling-last-minute-changes)
2. [Plus Points in Implementation (Overall Evaluation Criteria)](#2-plus-points-in-implementation-overall-evaluation-criteria)
   - [1. Authentication & Security Protocols](#1-authentication--security-protocols)
   - [2. Cost Estimation — Time and Space Complexity Analysis](#2-cost-estimation--time-and-space-complexity-analysis)
   - [3. Fault Tolerance & System Failure Handling](#3-fault-tolerance--system-failure-handling)
   - [4. Object-Oriented Programming (OOPS) Architecture](#4-object-oriented-programming-oops-architecture)
   - [5. System Design Trade-offs & Engineering Rationale](#5-system-design-trade-offs--engineering-rationale)
   - [6. System Monitoring, Telemetry & Real-Time Dashboards](#6-system-monitoring-telemetry--real-time-dashboards)
   - [7. Caching Strategy & Eviction Policies](#7-caching-strategy--eviction-policies)
   - [8. Error & Exception Handling Framework](#8-error--exception-handling-framework)
3. [Database Architecture & ER Diagram](#3-database-architecture--er-diagram)
4. [Database Access Guide (pgAdmin 4 & SQL)](#4-database-access-guide-pgadmin-4--sql)
5. [REST API Reference & Sample cURL Calls](#5-rest-api-reference--sample-curl-calls)
6. [Pre-Seeded Accounts & Credentials](#6-pre-seeded-accounts--credentials)
7. [Quick Start & Execution Guide](#7-quick-start--execution-guide)
8. [Automated Test Suite Verification](#8-automated-test-suite-verification)

---

## 1. Executive Summary & Problem Statement

Corporate employee commute management faces a massive optimization challenge: providing individual employee cabs is economically unsustainable, while ad-hoc carpooling results in excessive employee ride times, missed shift start times, and security risks.

**MoveInSync Cab Pooling Engine** provides an automated algorithmic dispatch platform that clusters employees traveling to the same office for the same shift, determines the shortest pickup sequence, enforces non-negotiable safety and commute thresholds, and dynamically re-plans when real-world changes occur.

```
                      ┌─────────────────────────────────────────┐
                      │ 1. Shift Booking Ingestion (Idempotent) │
                      └────────────────────┬────────────────────┘
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │ 2. Spatial Clustering (Capacity 4 / 6)  │
                      └────────────────────┬────────────────────┘
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │ 3. 2-Opt TSP Route Optimizer Engine     │
                      └────────────────────┬────────────────────┘
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │ 4. Max-Ride Time Validator (T ≤ 90 min) │
                      └────────────────────┬────────────────────┘
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │ 5. Night Safety Escort Attachment       │
                      └────────────────────┬────────────────────┘
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │ 6. Persist Manifest & Dispatch Itinerary│
                      └─────────────────────────────────────────┘
```

### I. Grouping Nearby Employees into Cabs
- **Cluster by Location**: Groups employees based on their verified home geographic coordinates (latitude and longitude). Uses spatial proximity heuristics so that riders in the same cab reside within neighboring zones (e.g. Koramangala and HSR Layout are grouped together; distant areas like Whitefield and Electronic City are never mixed).
- **Respect Seat Limit**: Cabs have fixed capacities ($K = 4$ or $K = 6$). A pool group strictly never exceeds the assigned vehicle's physical capacity.
- **Keep Detours Small**: Enforces a maximum detour threshold; riders are only pooled together if the marginal travel distance added by an additional pickup falls within acceptable bounds.
- **High-Volume Spatial Indexing**: Bookings are bucketed with **geohash** (precision 5 ≈ 4.9 km cells). Candidate riders are taken from the seed cell and its Moore neighbourhood, then filtered by `app.routing.max-cluster-radius-km` so far-apart areas are never mixed. Complexity ≈ $O(N \cdot C \cdot B)$ with $B \ll N$ (bucket size), not all-pairs $O(N^2)$.
- **Distance Models**: Default path uses **Haversine** (great-circle) for fast local computation. Optional **OSRM** road distances (`app.routing.osrm.enabled`) fall back to Haversine on timeout/failure.

### II. Best Pickup Order (Routing Engine)
- **Optimal Pickup Sequence**: Given $K$ employee pickup locations and 1 corporate office destination, determines the sequence minimizing total vehicle travel distance and drive time.
- **2-Opt TSP Heuristic**: Treats pickup ordering as an open Traveling Salesperson Problem (TSP). Begins with a nearest-neighbor baseline tour and iteratively uncrosses sub-optimal intersecting paths using 2-Opt edge swaps until local optimality is reached.
- **On-Time / Max-Ride Rule (Hard Unbreakable Rule)**: No employee may remain in the cab longer than the corporate maximum ride threshold (default: **90 minutes**). If any generated route ordering violates this threshold, the sequence is rejected by the validation pipeline and rejected from dispatch.
- **Pickup ETAs**: Backward-schedules from shift start time at the office, computing precise pickup timestamps for each employee along the route using calibrated distance and travel speeds.
- **Distance Models**: Default **Haversine** great-circle distance (≈30 km/h average speed for ETA). Optional **OSRM** road routing (`app.routing.osrm.enabled`) with automatic Haversine fallback when OSRM is disabled or unreachable.

### III. Night Safety Rules
- **No Woman Alone Rule**: In night shifts (between 20:00 and 06:00, or designated night outbound trips), a female employee must never be the first pickup (alone with the driver during pickup) or the last drop-off (alone with the driver at night).
- **Automated Route Rectification**:
  1. *Step 1 — Reordering*: The optimizer attempts to safely swap stop sequences so a male employee is the terminal stop without violating max-ride limits.
  2. *Step 2 — Escort Guard Provisioning*: If safe reordering is geometrically impossible, the engine automatically attaches a licensed security escort guard (`ESCORT_ASSIGNMENTS`), reserving 1 cab seat and logging the escort's name and contact on the trip manifest.

### IV. Handling Last-Minute Changes
- **Re-plan on Cancellation**: When an employee cancels a confirmed ride, the system recalculates *only* the affected cab's route, stops, and ETAs. All untouched cabs remain completely undisturbed.
- **Fit Late Bookings (Marginal Detour Insertion)**: When an employee books after initial allocation, the engine identifies the closest active cab with available capacity and evaluates the minimum marginal detour:
  $$\Delta \text{Detour}(i) = \text{dist}(p_{i-1}, \text{NewStop}) + \text{dist}(\text{NewStop}, p_i) - \text{dist}(p_{i-1}, p_i)$$
  If the candidate insertion satisfies both seat capacity and the 90-minute max-ride rule, the rider is slotted in dynamically.

---

## 2. Plus Points in Implementation (Overall Evaluation Criteria)

### 1. Authentication & Security Protocols
- **Stateless JSON Web Tokens (JWT)**: Secure user session management using HMAC-SHA256 signed tokens (`jjwt` library). Each token carries authenticated user identity, role authorities, user ID, and gender claims with a configurable 24-hour expiration window.
- **Salted BCrypt Password Hashing**: Passwords stored using Spring Security's `BCryptPasswordEncoder` (work factor 10), ensuring rainbow table resistance.
- **Role-Based Access Control (RBAC)**:
  - `ROLE_ADMIN`: Authorized to trigger automated clustering, manual re-optimization, add fleet vehicles, view all cab assignments, and manage escort rosters.
  - `ROLE_EMPLOYEE`: Restricted to self-service ride booking, personal trip itinerary inspection (`/api/assignments/my`), and cancellation of their own active rides.
- **Admin Self-Registration Blocked**: Public register cannot create `ROLE_ADMIN` unless `app.security.allow-admin-self-registration=true` (enabled only in tests). Production admins are seeded.
- **Idempotency & Duplicate-Safety**: Only one `CONFIRMED` booking per `(employee_id, shift_id, booking_date)` (partial unique index). Cancel→rebook reactivates the same row instead of inserting a duplicate seat.
- **Spring Security Filter Chain**: Custom `JwtAuthenticationFilter` validates JWTs. Public: `/api/auth/**`, `/actuator/health`, `/actuator/info`. Metrics require `ROLE_ADMIN`.

---

### 2. Cost Estimation — Time and Space Complexity Analysis

The Vehicle Routing Problem (VRP) is NP-hard. Rather than attempting factorial exhaustive permutations ($O(K!)$) which hang under high volume, MoveInSync leverages polynomial-time heuristics with bounded computational complexity.

| System Subsystem | Algorithm / Data Structure | Time Complexity (Best / Worst) | Space Complexity |
| :--- | :--- | :--- | :--- |
| **Spatial Clustering** | Geohash buckets + greedy seed / nearest neighbour + max radius | $O(N \cdot C \cdot B)$ ($B$ = bucket size ≪ $N$) | $O(N)$ buckets + pool |
| **Route Sequencing (TSP)** | Nearest-Neighbor + 2-Opt Edge Exchanges | $O(K^2)$ per cab ($K \le 6$) | $O(K)$ stop permutation array |
| **Max-Ride Time Check** | Cumulative Travel Duration Scanner | $O(K)$ per candidate route | $O(1)$ auxiliary |
| **Night Safety Audit** | Boundary Stop Gender Scanner | $O(K)$ | $O(1)$ |
| **Marginal Detour Insertion** | Best-Fit Insertion across $M$ active cabs | $O(M \cdot K)$ | $O(1)$ |
| **Cancellation Re-Route** | Local 2-Opt Re-optimization of single cab | $O(K^2)$ where $K \le 6$ | $O(K)$ |
| **Distance Matrix Lookup** | Thread-Safe In-Memory LRU Cache | $O(1)$ amortized | $O(S^2)$ where $S$ = unique stops |
| **Haversine Distance** | Great-Circle Trigonometric Equation | $O(1)$ constant time | $O(1)$ |

#### Algorithmic Complexity Breakdown:
1. **Spatial Clustering ($N$ bookings, cab capacity $K \in \{4, 6\}$)**:
   - Encode each booking into a geohash cell; pick farthest-from-office seeds; fill each cab from the seed neighbourhood within `max-cluster-radius-km`. Never exceeds capacity. Leftover riders stay unassigned rather than overflowing a cab.
2. **2-Opt Route Optimization ($K$ stops per vehicle)**:
   - For corporate cabs where $K \le 6$, NN + 2-Opt (≤50 iterations) is used instead of Held-Karp $O(2^K K^2)$. Max-ride (90 min) is validated after optimization, night reorder, late insert, and cancel replan.
3. **Space Complexity ($O(N)$)**:
   - Clustering and routing allocations scale with active bookings; the distance LRU cache is bounded at 10,000 entries.

---

### 3. Fault Tolerance & System Failure Handling
- **OSRM Fallback**: When `app.routing.osrm.enabled=true` and the HTTP call fails or times out, `OsrmDistanceProvider` degrades to Haversine so dispatch continues.
- **ACID Transactions**: Clustering, assignment, insertion, and cancellation run inside Spring `@Transactional` boundaries so partial writes roll back on failure.
- **Database Backup & Recovery**: PostgreSQL WAL + Flyway migrations (`V1` schema, `V2` seed, `V3` capacity/idempotency constraints).
- **Idempotent Bookings**: Duplicate active bookings are rejected; cancel→rebook reactivates the same row. Partial unique index enforces one `CONFIRMED` booking per employee/shift/date.
- **Live API only**: The React UI talks to Spring Boot (`/api`). Invalid JWTs are cleared; there is no mock-data fallback.

---

### 4. Object-Oriented Programming (OOPS) Architecture

MoveInSync is developed using **Java 21**, applying modern OOP patterns for maintainability, separation of concerns, and clean extensibility:

```
                            ┌────────────────────────┐
                            │   <<interface>>        │
                            │   DistanceProvider     │
                            └───────────┬────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
       ┌─────────────────────────┐             ┌─────────────────────────┐
       │ HaversineDistanceProvider│             │  OsrmDistanceProvider   │
       │ (Spherical Great-Circle)│             │ (Road Network REST API) │
       └─────────────────────────┘             └─────────────────────────┘
```

- **Encapsulation**: Domain entities (`Employee`, `Cab`, `CabAssignment`, `PickupStop`, `Booking`, `Shift`, `Office`) encapsulate their internal state using private fields, accessors, and Lombok `@Builder` patterns. Boundary invariants are validated during construction.
- **Abstraction & Strategy Pattern (Polymorphism)**:
  - The `DistanceProvider` interface abstracts distance and travel duration calculation:
    ```java
    public interface DistanceProvider {
        double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2);
        int calculateDurationMinutes(double lat1, double lon1, double lat2, double lon2);
    }
    ```
  - Concrete implementations (`HaversineDistanceProvider`, `OsrmDistanceProvider`) can be swapped seamlessly via Spring Dependency Injection without altering route optimization logic.
- **Single Responsibility Principle (SRP)**:
  - `AllocationService`: Clusters riders into groups respecting capacity.
  - `TwoOptRouteOptimizer`: Pure geometric traveling salesperson route sequencing.
  - `NightSafetyService`: Night hours compliance and escort guard assignment.
  - `ReplanningService`: Marginal detour insertion and single-cab cancellation re-routing.
- **Inheritance & Exception Hierarchy**: Custom business exceptions inherit from a common runtime base (`BusinessRuleException`, `ResourceNotFoundException`), enabling clean, centralized error handling.

---

### 5. System Design Trade-offs & Engineering Rationale

| Architectural Decision | Chosen Approach | Alternative Considered | Rationale & Trade-off |
| :--- | :--- | :--- | :--- |
| **Route Optimization** | 2-Opt Heuristic + Nearest Neighbor | Exact Held-Karp / Branch & Bound | Held-Karp $O(2^K K^2)$ has an exponential worst-case. For $K \le 6$, 2-Opt executes in $<0.5\text{ ms}$ with $<2\%$ optimality gap, ensuring fast response times under concurrent dispatch requests. |
| **Distance Engine** | Haversine ($1.3\times$ factor) + OSRM Strategy | Google Maps Distance Matrix API | Commercial APIs introduce financial costs (\$5/1000 calls) and ~200ms network latency per pair. Haversine provides $O(1)$ sub-microsecond offline evaluation with zero external dependencies. |
| **Spatial Indexing** | In-Memory Spatial Bucketing | PostGIS Extension | Utilizing relational PostgreSQL without requiring external native C-extensions keeps the architecture lightweight, cloud-portable, and simple to deploy in standard environments. |
| **Caching Layer** | In-memory LRU distance cache (10k) | Distributed Redis | Sufficient for single-node dispatch; avoids Redis ops cost for a case-study footprint. |
| **Constraint Enforcement** | Hard Unbreakable Rules | Soft Penalty Objectives | Seat capacity and the 90-minute max-ride limit are strictly rejected rather than penalized with weights, guaranteeing compliance with employee safety policies. |

---

### 6. System Monitoring, Telemetry & Real-Time Dashboards
- **Spring Boot Actuator**:
  - `/actuator/health` and `/actuator/info` are public.
  - `/actuator/metrics` requires `ROLE_ADMIN`.
- **Custom Micrometer Counters**:
  - `cabpooling.allocations.auto_cluster`
  - `cabpooling.allocations.bookings_assigned`
- **Interactive Web UI**: Admin dispatcher map + employee pickup ETAs from `/api/assignments/my`.
- **Structured SLF4J Logging**: Dispatch decisions, night-safety actions, and constraint violations.

---

### 7. Caching Strategy & Eviction Policies
- **Distance Matrix Memoization (`DistanceCacheService`)**:
  - Thread-safe **LRU** map keyed by directional coordinate pairs.
  - Cap: **10,000** entries (eldest evicted) — used heavily during NN + 2-Opt.
- **No Spring `@Cacheable` on offices/shifts** — those entities are cheap DB reads; distance caching is the hot path.

---

### 8. Error & Exception Handling Framework

All API errors are processed through a centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) conforming to the RFC 7807 problem details specification:

```json
{
  "status": 400,
  "error": "Business Rule Violation",
  "message": "Maximum ride time exceeded: Employee would ride for 102 mins (threshold: 90 mins)",
  "timestamp": "2026-09-24T02:00:00Z"
}
```

| HTTP Status | Trigger Condition | Example Handling |
| :--- | :--- | :--- |
| **400 Bad Request** | Business rule violation | Max-ride time exceeded, vehicle overcapacity, or booking past shift cutoff |
| **401 Unauthorized** | Missing or expired JWT token | Client redirected to login portal |
| **403 Forbidden** | Insufficient role authority | Employee attempting dispatcher-only fleet clustering |
| **404 Not Found** | Resource missing | Cab, Booking, Shift, or Employee ID does not exist |
| **409 Conflict** | Idempotency violation | Duplicate booking submission for the same shift and date |
| **422 Unprocessable** | Input validation failure | Malformed coordinate bounds, blank required fields, or invalid phone format |

---

## 3. Database Architecture & ER Diagram

```mermaid
erDiagram
    OFFICES ||--o{ SHIFTS : "hosts"
    SHIFTS ||--o{ BOOKINGS : "schedules"
    EMPLOYEES ||--o{ BOOKINGS : "reserves"
    SHIFTS ||--o{ CAB_ASSIGNMENTS : "allocates"
    CABS ||--o{ CAB_ASSIGNMENTS : "assigned to"
    CAB_ASSIGNMENTS ||--o{ PICKUP_STOPS : "contains ordered"
    BOOKINGS ||--o| PICKUP_STOPS : "maps to"
    CAB_ASSIGNMENTS ||--o| ESCORT_ASSIGNMENTS : "guarded by"

    OFFICES {
        bigint id PK
        varchar name
        varchar address
        double latitude
        double longitude
        timestamp created_at
    }

    EMPLOYEES {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar name
        varchar role
        varchar gender
        varchar phone_number
        varchar home_address
        double home_latitude
        double home_longitude
    }

    CABS {
        bigint id PK
        varchar license_plate UK
        varchar model
        int capacity
        varchar driver_name
        varchar driver_phone
        boolean is_active
    }

    SHIFTS {
        bigint id PK
        bigint office_id FK
        varchar name
        time start_time
        time end_time
        varchar shift_type
        int cutoff_minutes
    }

    BOOKINGS {
        bigint id PK
        bigint employee_id FK
        bigint shift_id FK
        date booking_date
        double pickup_latitude
        double pickup_longitude
        varchar pickup_address
        varchar status
    }

    CAB_ASSIGNMENTS {
        bigint id PK
        bigint cab_id FK
        bigint shift_id FK
        date assignment_date
        varchar status
        double total_distance_km
        int total_duration_minutes
        boolean has_escort
    }

    PICKUP_STOPS {
        bigint id PK
        bigint cab_assignment_id FK
        bigint booking_id FK
        int stop_order
        varchar stop_type
        double latitude
        double longitude
        varchar address
        timestamp planned_time
        double distance_from_previous_km
        int duration_from_previous_minutes
    }

    ESCORT_ASSIGNMENTS {
        bigint id PK
        bigint cab_assignment_id FK
        varchar escort_name
        varchar escort_contact
        varchar status
    }
```

---

## 4. Database Access Guide (pgAdmin 4 & SQL)

### Connection Parameters
| Parameter | Value |
| :--- | :--- |
| **Host Name / Address** | `localhost` (or `127.0.0.1`) |
| **Port** | `5432` |
| **Maintenance Database** | `employee_cab_pooling` |
| **Username** | `postgres` |
| **Password** | `postgres123` |

### Step-by-Step pgAdmin 4 Access:
1. Open **pgAdmin 4**.
2. Right-click **Servers** > **Register** > **Server...**
3. In the **General** tab, set **Name** to: `MoveInSync PostgreSQL`.
4. In the **Connection** tab, fill:
   - **Host**: `localhost`
   - **Port**: `5432`
   - **Maintenance database**: `employee_cab_pooling`
   - **Username**: `postgres`
   - **Password**: `postgres123` (check *Save password*)
5. Click **Save**.
6. Expand: `MoveInSync PostgreSQL` > `Databases` > `employee_cab_pooling` > `Schemas` > `public` > `Tables`.
7. Right-click any table (e.g. `employees` or `bookings`) and select **View/Edit Data** > **All Rows**.

### Useful SQL Inspection Queries:
```sql
-- View all registered employees and their roles
SELECT id, name, email, role, gender, home_address, home_latitude, home_longitude 
FROM employees 
ORDER BY id ASC;

-- Inspect active bookings by shift and date
SELECT b.id, e.name AS employee_name, s.name AS shift_name, b.booking_date, b.status 
FROM bookings b
JOIN employees e ON b.employee_id = e.id
JOIN shifts s ON b.shift_id = s.id
ORDER BY b.booking_date DESC;

-- View optimized cab assignments with escort guard status
SELECT ca.id, c.license_plate, c.model, ca.total_distance_km, ca.total_duration_minutes, ca.has_escort, ca.status
FROM cab_assignments ca
JOIN cabs c ON ca.cab_id = c.id;

-- Inspect planned pickup sequence and ETAs for a cab assignment
SELECT ps.stop_order, ps.stop_type, e.name AS passenger, ps.address, ps.planned_time, ps.duration_from_previous_minutes
FROM pickup_stops ps
LEFT JOIN bookings b ON ps.booking_id = b.id
LEFT JOIN employees e ON b.employee_id = e.id
WHERE ps.cab_assignment_id = 1
ORDER BY ps.stop_order ASC;
```

---

## 5. REST API Reference & Sample cURL Calls

### 1. Register New User (Employee or Admin)
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Kavita Rao",
    "email": "kavita.rao@moveinsync.com",
    "password": "password123",
    "role": "ROLE_EMPLOYEE",
    "gender": "FEMALE",
    "phoneNumber": "+91 98765 43210",
    "homeAddress": "Koramangala 5th Block, Bengaluru",
    "homeLatitude": 12.9352,
    "homeLongitude": 77.6245
  }'
```

### 2. User Authentication (Login)
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@moveinsync.com","password":"password123"}'
```

### 3. Verify Current Authenticated Profile
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 4. Create Shift Ride Booking (Idempotent)
```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer <EMPLOYEE_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "shiftId": 1,
    "bookingDate": "2026-09-25",
    "pickupAddress": "Indiranagar 100ft Road, Bengaluru",
    "pickupLatitude": 12.9719,
    "pickupLongitude": 77.6412
  }'
```

### 5. Auto-Cluster Bookings into Cabs (Admin Dispatcher)
```bash
curl -X POST http://localhost:8080/api/allocations/auto-cluster \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"shiftId": 1, "assignmentDate": "2026-09-25"}'
```

### 6. Re-Optimize Route with 2-Opt Algorithm
```bash
curl -X POST http://localhost:8080/api/allocations/optimize/1 \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### 7. Dynamic Late Booking Slotting
```bash
curl -X POST http://localhost:8080/api/allocations/insert-booking/5 \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```

### 8. Cancel Booking & Trigger Live Re-route
```bash
curl -X DELETE http://localhost:8080/api/bookings/2 \
  -H "Authorization: Bearer <EMPLOYEE_OR_ADMIN_TOKEN>"
```

---

## 6. Pre-Seeded Accounts & Credentials

The system initializes standard accounts upon backend launch via `DataInitializer.java`:

| Account Role | Email | Password | Assigned Persona & Locality |
| :--- | :--- | :--- | :--- |
| **Admin Dispatcher** | `admin@moveinsync.com` | `password123` | Chief Dispatcher Rajesh (MG Road Corporate Center) |
| **Employee (Female)** | `pooja.sharma@moveinsync.com` | `password123` | Pooja Sharma (100ft Road, Indiranagar) |
| **Employee (Male)** | `arun.kumar@moveinsync.com` | `password123` | Arun Kumar (HSR Layout Sector 1) |
| **Employee (Female)** | `sneha.reddy@moveinsync.com` | `password123` | Sneha Reddy (Koramangala 4th Block) |
| **Employee (Male)** | `vikas.mehta@moveinsync.com` | `password123` | Vikas Mehta (Sarjapur Main Road) |
| **Employee (Female)** | `ananya.iyer@moveinsync.com` | `password123` | Ananya Iyer (BTM Layout 2nd Stage) |
| **Employee (Male)** | `rohit.verma@moveinsync.com` | `password123` | Rohit Verma (Marathahalli Bridge) |

---

## 7. Quick Start & Execution Guide

### Prerequisites
- **Java**: OpenJDK 21 or later
- **Maven**: 3.9+
- **Node.js**: 18+ (with npm)
- **PostgreSQL**: 16+ or Docker

---

### Option A: One-Click Development Launcher (Recommended)
On **Windows**, double-click:
```cmd
start-dev.bat
```
On **Linux / macOS**, execute:
```bash
chmod +x start-dev.sh
./start-dev.sh
```

---

### Option B: Docker Compose (Full Stack)
To run PostgreSQL, backend, and frontend together via Docker:
```bash
docker-compose up -d
```

---

### Option C: Manual Service Launch

#### 1. Start PostgreSQL Database
Ensure PostgreSQL is active on port `5432` with database `employee_cab_pooling`.

#### 2. Start Spring Boot Backend (Java 21)
```bash
cd employee_cab_polling
mvn spring-boot:run
```
- REST API is available at: `http://127.0.0.1:8080/api`
- Actuator Health check: `http://127.0.0.1:8080/actuator/health`

#### 3. Start React + TypeScript Frontend
```bash
cd frontend
npm install
npm run dev
```
- Web Application is available at: `http://localhost:3000`

---

## 8. Automated Test Suite Verification

Execute the integration test suite validating business rules, security, routing, and database constraints:

```bash
cd employee_cab_polling
mvn test
```

### Integration Test Coverage Breakdown (93 / 93 passing):
- **`HardConstraintsUnitTest`**: Max-ride reject/accept, capacity 4|6 rule, geohash neighbourhood indexing.
- **`AllocationRouteOptimizationIntegrationTests`**: Spatial clustering, 2-Opt TSP, night escort auto-attach, distance cache.
- **`LiveReplanningIntegrationTests`**: Cancel local replan, late insert + detour, full-cab (capacity 4) rejection, cancel→rebook idempotency.
- **`EscortIntegrationTests`**: Manual escort CRUD + RBAC.
- **`AuthIntegrationTests`**: JWT issue/validation, BCrypt, RBAC endpoint guards.
- **`CabAssignmentIntegrationTests`**: Manifest generation, NN+2-Opt build-route, ETA sequencing.
- **`CabIntegrationTests`**: Fleet CRUD; capacity outside `{4,6}` rejected.
- **`ShiftBookingIntegrationTests` / `OfficeEmployeeIntegrationTests` / `RepositoryIntegrationTests`**: Booking lifecycle, offices, Flyway constraints.

---
*Developed for LPU Backend Case Studies 2026 — MoveInSync Smart Employee Cab Pooling Platform.*
