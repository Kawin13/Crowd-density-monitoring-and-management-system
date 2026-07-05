@echo off
echo ====================================================
echo  Crowd Density Monitoring System - Local Setup
echo ====================================================

echo.
echo [1/4] Setting up database...
echo Run this in MySQL Workbench or MySQL CLI:
echo   source database/schema.sql
echo   source database/data.sql
echo.
pause

echo [2/4] Starting AI Service...
cd ai-service
if not exist venv (
    python -m venv venv
)
call venv\Scripts\activate
pip install -r requirements.txt -q
start "AI Service" cmd /k "python main.py"
cd ..

echo.
echo [3/4] Starting Backend...
cd backend
start "Backend" cmd /k "mvn spring-boot:run"
cd ..

echo.
echo [4/4] Starting Frontend...
cd frontend
if not exist node_modules (
    npm install
)
start "Frontend" cmd /k "npm run dev"
cd ..

echo.
echo ====================================================
echo  All services starting...
echo  Frontend : http://localhost:5173
echo  Backend  : http://localhost:8080
echo  AI Svc   : http://localhost:8000
echo  Login    : admin / Admin@1234
echo ====================================================
pause
