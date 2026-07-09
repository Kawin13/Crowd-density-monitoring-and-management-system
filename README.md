# Crowd Density Monitoring & Management System

**An AI-powered platform that turns raw CCTV footage into real-time occupancy intelligence** — so operations teams know a space is unsafe *before* it becomes an incident, not after.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![Python](https://img.shields.io/badge/Python-FastAPI-009688?logo=fastapi&logoColor=white)
![YOLOv8](https://img.shields.io/badge/YOLOv8-Ultralytics-black?logo=yolo&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

---

## Why This Exists

Venues like malls, stadiums, transit hubs, and event spaces need to know — **continuously, not just after an incident** — how full a space is relative to its safe capacity. This system automates that end to end: cameras feed a computer-vision model that counts people, the platform converts that count into an occupancy percentage and risk level, and staff are alerted automatically the moment a location crosses a safety threshold.

It's built as three independently deployable services (dashboard, API, AI inference), the way a production system would actually be architected — not a single monolithic script.

---

## Highlights

- **Real-time people counting** from live RTSP camera streams *and* uploaded video, powered by YOLOv8 object detection
- **Automated risk classification** — a five-level scale (Low → Overcrowded) computed live against each location's configured capacity, so risk is visible at a glance without anyone doing the math
- **Alerting that doesn't rely on a human watching a screen** — automatic threshold-crossing alerts with history and bulk acknowledgement
- **Full analytics suite** — hourly trends, peak-hour analysis, and cross-location crowd distribution
- **Exportable compliance reporting** — PDF and Excel reports by location and date range
- **Role-based access control** (Admin / Operator / Viewer) so permissions match responsibility
- **Multi-source ingestion** — CCTV (RTSP), mobile devices, and direct video upload, all through the same pipeline

---

## System Architecture

Three independently deployable services, coordinated over REST:

```
┌─────────────────┐      ┌──────────────────┐      ┌────────────────────┐
│   Frontend       │◄────►│   Backend API     │◄────►│   AI Service        │
│   React + Vite   │      │   Spring Boot     │      │   FastAPI + YOLOv8  │
│                  │      │   (Auth, Users,   │      │   (Person detection │
│   Dashboard,     │      │   Cameras, Alerts,│      │   on live streams   │
│   monitoring UI, │      │   Analytics,      │      │   & uploaded video) │
│   reporting      │      │   Reports)        │      │                     │
└─────────────────┘      └────────┬─────────┘      └────────────────────┘
                                    │
                              ┌─────▼─────┐
                              │  MySQL 8   │
                              └───────────┘
```

This separation keeps the compute-heavy AI workload isolated from the transactional API, so each layer can be scaled, restarted, or replaced independently — the AI service can go down without taking the dashboard or user management with it.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite, Recharts |
| Backend API | Java 21, Spring Boot, Spring Security (JWT) |
| AI Service | Python, FastAPI, YOLOv8 (Ultralytics) |
| Database | MySQL 8 |
| Deployment | Docker, Docker Compose |

---

## Engineering Depth

Beyond the feature set, this project reflects production-grade engineering practices:

- **Stateless JWT authentication** with refresh-token rotation and role-based authorization
- **Audit logging** of key account and administrative actions, written asynchronously off the request thread so a slow or contended database write can never add latency to a user-facing action
- **Defensive error handling** on every AI-service integration point, so a slow or unreachable AI service degrades gracefully instead of breaking the dashboard
- **Query-level performance tuning** on analytics and dashboard endpoints to keep multi-camera views responsive as data volume grows
- **Race-condition-safe token refresh** on the frontend, so concurrent expired-session requests share a single refresh call instead of racing each other

---

## Core Modules & API Surface

**Authentication** — register, login, refresh, logout, password reset
**Cameras** — CRUD, start/stop monitoring per camera
**Monitoring** — live/historical crowd data, video upload, on-demand AI analysis
**Alerts** — active/acknowledged alert feed, bulk acknowledgement, live unacknowledged count
**Analytics & Reports** — daily/weekly/monthly analytics, peak-hour analysis, PDF/Excel export

<details>
<summary>Full endpoint reference</summary>

**Authentication**
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register user |
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/refresh` | Refresh token |
| POST | `/api/auth/logout` | Logout |
| POST | `/api/auth/forgot-password` | Request reset link |
| POST | `/api/auth/reset-password` | Reset password |

**Cameras**
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/cameras` | List all cameras |
| POST | `/api/cameras` | Add camera |
| PUT | `/api/cameras/{id}` | Update camera |
| DELETE | `/api/cameras/{id}` | Delete camera |
| POST | `/api/cameras/{id}/start` | Start monitoring |
| POST | `/api/cameras/{id}/stop` | Stop monitoring |

**Monitoring**
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/monitoring/cameras/{id}/latest` | Latest crowd data |
| GET | `/api/monitoring/cameras/{id}/history` | Historical data |
| POST | `/api/monitoring/cameras/{id}/data` | Save detection data |
| POST | `/api/monitoring/cameras/{id}/upload-video` | Upload video |
| POST | `/api/monitoring/cameras/{id}/analyze-stream` | Trigger AI analysis |

**Alerts**
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/alerts` | All alerts (paginated) |
| GET | `/api/alerts/active` | Unacknowledged alerts |
| PUT | `/api/alerts/{id}/acknowledge` | Acknowledge alert |
| PUT | `/api/alerts/acknowledge-all` | Acknowledge all |
| GET | `/api/alerts/count` | Unacknowledged count |

**Analytics & Reports**
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/analytics/daily` | Daily analytics |
| GET | `/api/analytics/weekly` | Weekly analytics |
| GET | `/api/analytics/monthly` | Monthly analytics |
| GET | `/api/analytics/peak-hours` | Peak hour analysis |
| GET | `/api/reports/pdf` | Download PDF report |
| GET | `/api/reports/excel` | Download Excel report |

</details>

---

## The Occupancy Model

```
Occupancy % = (Detected People / Maximum Capacity) × 100

  0–25%   →  LOW
 26–50%   →  MEDIUM
 51–75%   →  HIGH
 76–100%  →  CRITICAL
   >100%  →  OVERCROWDED
```

---

## Run Commands

**Backend**
```bash
cd backend
mvn clean install
mvn spring-boot:run
# http://localhost:8080
```

**AI Service**
```bash
cd ai-service
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
python main.py
# http://localhost:8000
```

**Frontend**
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

**All services via Docker**
```bash
docker compose up --build
```

---

## Project Structure

```
crowd-density-monitoring-system/
├── backend/       Spring Boot REST API
├── frontend/      React dashboard
├── ai-service/    FastAPI + YOLOv8 detection service
└── database/      MySQL schema and seed data
```

---

*Built as a full end-to-end system — computer vision, a transactional backend, and a real operator-facing dashboard — rather than a single-layer demo.*