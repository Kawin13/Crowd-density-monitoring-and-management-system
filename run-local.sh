#!/bin/bash

echo "======================================================"
echo " Crowd Density Monitoring System - Local Setup"
echo "======================================================"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}[1/4] Database setup${NC}"
echo "Please ensure MySQL is running and run:"
echo "  mysql -u root -p1310 < database/schema.sql"
echo "  mysql -u root -p1310 crowd_monitoring < database/data.sql"
read -p "Press Enter once DB is ready..."

echo -e "\n${YELLOW}[2/4] Starting AI Service...${NC}"
cd ai-service
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi
source venv/bin/activate
pip install -r requirements.txt -q
uvicorn main:app --host 0.0.0.0 --port 8000 &
AI_PID=$!
echo -e "${GREEN}AI Service started (PID: $AI_PID)${NC}"
cd ..

echo -e "\n${YELLOW}[3/4] Starting Backend...${NC}"
cd backend
mvn spring-boot:run &
BACKEND_PID=$!
echo -e "${GREEN}Backend started (PID: $BACKEND_PID)${NC}"
cd ..

echo -e "\n${YELLOW}[4/4] Starting Frontend...${NC}"
cd frontend
[ ! -d "node_modules" ] && npm install
npm run dev &
FRONTEND_PID=$!
echo -e "${GREEN}Frontend started (PID: $FRONTEND_PID)${NC}"
cd ..

echo -e "\n======================================================"
echo -e "${GREEN} All services started!${NC}"
echo " Frontend : http://localhost:5173"
echo " Backend  : http://localhost:8080"
echo " AI Svc   : http://localhost:8000"
echo " Login    : admin / Admin@1234"
echo "======================================================"
echo "Press Ctrl+C to stop all services"

trap "kill $AI_PID $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT
wait
