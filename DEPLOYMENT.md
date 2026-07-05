# Deployment Guide

This stack has four pieces (frontend, backend, AI service, database), and no single free host fits all of them well — the AI service in particular needs more RAM than a typical free web-service tier gives you. The plan below puts each piece on the free tier best suited to it:

| Service    | Where            | Why                                                          |
|------------|------------------|---------------------------------------------------------------|
| Database   | Aiven (MySQL)    | Genuinely free forever — 1GB RAM/storage, no card, no expiry  |
| Backend    | Render           | Free web service, deploys straight from GitHub                |
| AI Service | Hugging Face Spaces | Free CPU tier gives 16GB RAM — the YOLOv8/PyTorch service needs that, Render's 512MB free tier does not |
| Frontend   | Vercel           | Free static hosting, first-class Vite/React support           |

---

## Part 1 — Push the code to GitHub

1. Create a new empty repository on GitHub (no README/license, so it stays empty): **github.com → New repository**.
2. From the project root:
   ```bash
   cd crowd-density-monitoring-system
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. A `.gitignore` is already included, so `target/`, `node_modules/`, `venv/`, and local `.env` files won't be pushed.

---

## Part 2 — Database (Aiven, free MySQL)

1. Sign up at **aiven.io** (no card required) and create a new **MySQL** service on the free plan.
2. Once it's running, open the service's **Overview** page and copy: host, port, username, password, and database name (or create a database named `crowd_monitoring`).
3. Connect with any MySQL client using those details and run:
   ```bash
   mysql -h <host> -P <port> -u <user> -p <database> < database/schema.sql
   mysql -h <host> -P <port> -u <user> -p <database> < database/data.sql
   ```
4. Keep the connection string handy — you'll paste it into the backend's environment variables next.

---

## Part 3 — Backend (Render, Spring Boot)

1. Sign up at **render.com** and connect your GitHub account.
2. **New → Web Service** → select your repository → set **Root Directory** to `backend`.
3. Render detects the `Dockerfile` in `backend/` automatically — leave the build settings as Docker.
4. Under **Environment Variables**, add:
   | Key | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<aiven-host>:<aiven-port>/crowd_monitoring?useSSL=true&serverTimezone=Asia/Kolkata` |
   | `SPRING_DATASOURCE_USERNAME` | your Aiven username |
   | `SPRING_DATASOURCE_PASSWORD` | your Aiven password |
   | `APP_JWT_SECRET` | a long random string (generate one, don't reuse the sample from `application.properties`) |
   | `APP_AI_SERVICE_URL` | leave blank for now — filled in after Part 4 |
   | `APP_CORS_ALLOWED_ORIGINS` | leave blank for now — filled in after Part 5 |
   | `APP_FRONTEND_URL` | leave blank for now — filled in after Part 5 |
5. Choose the **Free** instance type and deploy. Note the resulting URL, e.g. `https://crowd-backend.onrender.com`.

---

## Part 4 — AI Service (Hugging Face Spaces, FastAPI + YOLOv8)

1. Sign up at **huggingface.co** → **New Space**.
2. Choose SDK: **Docker**, visibility: your choice, hardware: **CPU basic (free)**.
3. Push the contents of `ai-service/` to the Space's git repo (Spaces are git repos too):
   ```bash
   cd ai-service
   git init
   git remote add space https://huggingface.co/spaces/<your-username>/<space-name>
   git add .
   git commit -m "AI service"
   git push space main
   ```
4. At the top of `ai-service/README.md` (create one if it doesn't exist), add this metadata block so the Space knows which port to route to:
   ```yaml
   ---
   title: Crowd AI Service
   sdk: docker
   app_port: 8000
   ---
   ```
5. Once it builds, your AI service URL will be `https://<your-username>-<space-name>.hf.space`. Go back to Render and set the backend's `APP_AI_SERVICE_URL` to that URL, then redeploy the backend.

---

## Part 5 — Frontend (Vercel, React/Vite)

1. Sign up at **vercel.com**, connect GitHub, **Add New → Project**, select your repo, set **Root Directory** to `frontend`.
2. Framework preset: Vite. Under **Environment Variables**, add:
   | Key | Value |
   |---|---|
   | `VITE_API_URL` | `https://<your-render-backend-url>/api` |
3. Deploy. Note the resulting URL, e.g. `https://crowd-monitor.vercel.app`.
4. Go back to Render and set on the backend:
   - `APP_CORS_ALLOWED_ORIGINS` → your Vercel URL
   - `APP_FRONTEND_URL` → your Vercel URL
   Redeploy the backend so the new values take effect.

---

## Part 6 — Verify

1. Open the Vercel URL and log in with a [demo account](./README.md#demo-credentials).
2. Add a camera and try **Analyze Now** — this exercises the full chain: frontend → Render backend → Hugging Face AI service → back to the backend → MySQL on Aiven.
3. First request to the AI service may be slow (~30–60s) if the Space or Render's free web service had spun down from inactivity — this is expected on free tiers, not a bug.

## Notes on Free-Tier Behavior

- **Render free web services** spin down after 15 minutes idle and take 30–60s to wake on the next request.
- **Hugging Face free CPU Spaces** sleep after 48 hours idle — more forgiving than Render, but still not always-on.
- **Aiven's free MySQL** has no expiry and stays on, so it's the one piece here that behaves like a normal always-on database.
- None of this is production-grade uptime — it's meant for demos, evaluation, and portfolio use. For real production traffic, move each piece to a paid tier of the same platform.
