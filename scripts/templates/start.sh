#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 1. Check Java
JAVA_CMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
    echo "[ERROR] 找不到 Java 執行環境！"
    echo "本系統需要 Java 21 (JDK 21) 或以上版本。"
    echo "請前往官方網站下載安裝 OpenJDK 21："
    echo "https://adoptium.net/temurin/releases/?version=21"
    exit 1
fi

# 2. Port Configuration
PORT="${1:-8080}"
STOP_PORT="18080"
STOP_KEY="formosabearv"

# Check port occupation
if command -v lsof >/dev/null 2>&1; then
    if lsof -Pi :"$PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "[WARNING] 連接埠 $PORT 目前已被其他程式佔用！"
        echo "您可以傳入其他連接埠啟動，例如：./start.sh 8088"
    fi
fi

# 3. Storage Directory
DOCS_HOME="${DOCS_HOME:-$DIR/data}"
mkdir -p "$DOCS_HOME"

echo "========================================================"
echo "  FormosaBearV (v2.02) 免安裝可攜綠色伺服器"
echo "========================================================"
echo "  系統網址：    http://localhost:$PORT/"
echo "  資料庫路徑：  $DOCS_HOME"
echo "  停止方式：    執行 ./stop.sh 或按下 Ctrl+C"
echo "========================================================"
echo "伺服器啟動中，請稍候..."
echo ""

# Auto open browser
(
    sleep 3
    if command -v xdg-open >/dev/null 2>&1; then
        xdg-open "http://localhost:$PORT/" >/dev/null 2>&1 &
    elif command -v open >/dev/null 2>&1; then
        open "http://localhost:$PORT/" >/dev/null 2>&1 &
    fi
) &

exec "$JAVA_CMD" -Ddocs.home="$DOCS_HOME" -Djetty.http.port="$PORT" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar
