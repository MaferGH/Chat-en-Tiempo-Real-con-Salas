#!/usr/bin/env bash
# =============================================================
#  build.sh  —  Compila y empaqueta el chat NIO en un JAR
#  Requiere: Java JDK 11+
# =============================================================
set -e

SRC="src"
OUT="out"
JAR="ChatNIO.jar"

echo "==> Limpiando directorio de salida..."
rm -rf "$OUT"
mkdir -p "$OUT"

echo "==> Compilando fuentes Java..."
javac -encoding UTF-8 -d "$OUT" "$SRC"/*.java

echo "==> Empaquetando JAR..."
jar cfm "$JAR" MANIFEST.MF -C "$OUT" .

echo ""
echo "✅  Build completado: $JAR"
echo ""
echo "Para iniciar el SERVIDOR:"
echo "   java -jar $JAR --server [puerto]"
echo "   Ejemplo: java -jar $JAR --server 9090"
echo ""
echo "Para iniciar el CLIENTE (abre la ventana de login):"
echo "   java -jar $JAR"
