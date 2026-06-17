@echo off
REM =============================================================
REM  build.bat  —  Compila y empaqueta el chat NIO en un JAR
REM  Requiere: Java JDK 11+  (javac y jar en el PATH)
REM =============================================================

set SRC=src
set OUT=out
set JAR=ChatNIO.jar

echo ==^> Limpiando directorio de salida...
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

echo ==^> Compilando fuentes Java...
javac -encoding UTF-8 -d "%OUT%" %SRC%\*.java
if %ERRORLEVEL% neq 0 (
    echo ERROR: La compilacion fallo.
    pause
    exit /b 1
)

echo ==^> Empaquetando JAR...
jar cfm %JAR% MANIFEST.MF -C "%OUT%" .
if %ERRORLEVEL% neq 0 (
    echo ERROR: No se pudo crear el JAR.
    pause
    exit /b 1
)

echo.
echo [OK] Build completado: %JAR%
echo.
echo Para iniciar el SERVIDOR:
echo    java -jar %JAR% --server 9090
echo.
echo Para iniciar el CLIENTE:
echo    java -jar %JAR%
echo.
pause
