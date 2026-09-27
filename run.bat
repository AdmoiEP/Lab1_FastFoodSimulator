@echo off
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo JDK was not found. Install JDK 8 or newer and open a new terminal.
  pause
  exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -sourcepath src -d out src\fastfood\Main.java
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
java -cp out fastfood.Main