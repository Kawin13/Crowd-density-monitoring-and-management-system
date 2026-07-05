# Crowd Density Monitoring & Management System

An AI-powered platform that monitors crowd density in real time from CCTV, mobile, and uploaded video sources, converts raw people-counts into actionable occupancy alerts, and gives operations teams a single dashboard to manage capacity, safety, and reporting across multiple locations.

## Overview

Venues such as malls, stadiums, transit hubs, and event spaces need to know — continuously, not just after an incident — how full a space is relative to its safe capacity. This system automates that process end to end: cameras feed a computer-vision model that counts people, the platform converts that count into an occupancy percentage and risk level, and staff are alerted automatically when a location crosses a safety threshold.

## Key Features

- **Real-time people counting** from live camera streams and uploaded video, powered by YOLOv8
- **Capacity-based occupancy scoring** — each location's live count is measured against its configured maximum capacity
- **Five-level risk classification** (Low / Medium / High / Critical / Overcrowded) so risk is visible at a glance
- **Automated alerting** with history and bulk acknowledgement, so nothing relies on someone watching a screen
- **Analytics dashboards** — hourly trends, peak-hours analysis, and crowd distribution across all monitored locations
- **Exportable reporting** — PDF and Excel reports by location and date range for compliance and management review
- **Role-based access control** (Admin / Operator / Viewer) so permissions match responsibility
- **Multi-source camera support** — CCTV (RTSP), mobile devices, and direct video upload
- **Light/dark themes** for operators working extended monitoring shifts

## Tech Stack

| Layer         | Technology                                   |
|---------------|-----------------------------------------------|
| Frontend      | React 18, Vite, Recharts                      |
| Backend API   | Java 21, Spring Boot, Spring Security (JWT)   |
| AI Service    | Python, FastAPI, YOLOv8 (Ultralytics)         |
| Database      | MySQL 8                                       |
| Deployment    | Docker, Docker Compose                        |

## System Architecture

The system is split into three independently deployable services, coordinated over REST:

1. **Frontend** — the operator-facing dashboard (React) for live monitoring, camera management, alerts, analytics, and reports.
2. **Backend API** — the Spring Boot service that owns authentication, camera and user management, alert generation, analytics, and report generation, backed by MySQL.
3. **AI Service** — a Python/FastAPI microservice that runs YOLOv8 person-detection against camera streams or uploaded video and returns a people count to the backend.

This separation keeps the compute-heavy AI workload isolated from the transactional API, so each layer can be scaled or replaced independently.

## Security & Reliability

- Stateless JWT authentication with refresh tokens and role-based authorization
- Audit logging of key account and administrative actions
- Defensive error handling on every AI-service integration point, so a slow or unreachable AI service degrades gracefully instead of breaking the dashboard
- Query-level performance tuning on the analytics and dashboard endpoints to keep multi-camera views responsive as data volume grows

## Project Structure

```
crowd-density-monitoring-system/
├── backend/       Spring Boot REST API
├── frontend/      React dashboard
├── ai-service/    FastAPI + YOLOv8 detection service
└── database/      MySQL schema and seed data
```

---

## Getting Started

### Prerequisites
- Java 21, Maven 3.9+
- Node.js 20+
- Python 3.11+
- MySQL 8

### 1. Database

```sql
CREATE DATABASE crowd_monitoring;
USE crowd_monitoring;
source database/schema.sql;
source database/data.sql;
```

### 2. Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
# http://localhost:8080
```

### 3. AI Service

```bash
cd ai-service
python -m venv venv
venv\Scripts\activate      # Windows
source venv/bin/activate   # Linux/Mac

pip install -r requirements.txt
python main.py
# http://localhost:8000
```

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

### One-Command Start

```bash
run-local.bat          # Windows
./run-local.sh          # Linux/Mac (chmod +x run-local.sh first)
```

### Docker (all services)

```bash
docker compose up --build
```

See [DEPLOYMENT.md](./DEPLOYMENT.md) for cloud / free-tier deployment notes.

## Demo Credentials

These three accounts are seeded automatically on first backend startup, and self-healed (password, role, and enabled state corrected) on every restart, so they always work even against an older database.

| Role     | Username    | Password         | Access                            |
|----------|-------------|-------------------|-------------------------------------|
| Admin    | `admin`     | `Admin@1234`      | Full system access                  |
| Operator | `operator1` | `Operator@1234`   | Camera management and monitoring    |
| Viewer   | `viewer1`   | `Viewer@1234`     | View-only access                    |

If a demo login fails after pulling new backend changes, restart the backend once — the self-heal step only runs at startup.

## API Reference

### Authentication
| Method | Endpoint                    | Description        |
|--------|-----------------------------|--------------------|
| POST   | /api/auth/register          | Register user      |
| POST   | /api/auth/login             | Login              |
| POST   | /api/auth/refresh           | Refresh token      |
| POST   | /api/auth/logout            | Logout             |
| POST   | /api/auth/forgot-password   | Request reset link |
| POST   | /api/auth/reset-password    | Reset password     |

### Cameras
| Method | Endpoint                          | Description         |
|--------|-----------------------------------|---------------------|
| GET    | /api/cameras                      | List all cameras    |
| POST   | /api/cameras                      | Add camera          |
| PUT    | /api/cameras/{id}                 | Update camera       |
| DELETE | /api/cameras/{id}                 | Delete camera       |
| POST   | /api/cameras/{id}/start           | Start monitoring    |
| POST   | /api/cameras/{id}/stop            | Stop monitoring     |

### Monitoring
| Method | Endpoint                                       | Description          |
|--------|-------------------------------------------------|----------------------|
| GET    | /api/monitoring/cameras/{id}/latest            | Latest crowd data    |
| GET    | /api/monitoring/cameras/{id}/history           | Historical data      |
| POST   | /api/monitoring/cameras/{id}/data              | Save detection data  |
| POST   | /api/monitoring/cameras/{id}/upload-video      | Upload video         |
| POST   | /api/monitoring/cameras/{id}/analyze-stream    | Trigger AI analysis  |

### Alerts
| Method | Endpoint                    | Description              |
|--------|-----------------------------|---------------------------|
| GET    | /api/alerts                 | All alerts (paginated)    |
| GET    | /api/alerts/active          | Unacknowledged alerts     |
| PUT    | /api/alerts/{id}/acknowledge| Acknowledge alert         |
| PUT    | /api/alerts/acknowledge-all | Acknowledge all           |
| GET    | /api/alerts/count           | Unacknowledged count      |

### Analytics & Reports
| Method | Endpoint                   | Description            |
|--------|-----------------------------|------------------------|
| GET    | /api/analytics/daily       | Daily analytics         |
| GET    | /api/analytics/weekly      | Weekly analytics        |
| GET    | /api/analytics/monthly     | Monthly analytics       |
| GET    | /api/analytics/peak-hours  | Peak hour analysis      |
| GET    | /api/reports/pdf           | Download PDF report     |
| GET    | /api/reports/excel         | Download Excel report   |

## Occupancy Formula

```
Occupancy % = (Detected People / Maximum Capacity) × 100

0–25%   → LOW
26–50%  → MEDIUM
51–75%  → HIGH
76–100% → CRITICAL
> 100%  → OVERCROWDED
```


