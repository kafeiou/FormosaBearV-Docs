#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 1. Check for Java 21+ Runtime (from JAVA_HOME or PATH)
JAVA_CMD=""

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    if "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "(21|2[2-9])\.'; then
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi
fi

if [ -z "$JAVA_CMD" ] && command -v java >/dev/null 2>&1; then
    if java -version 2>&1 | grep -qE 'version "(21|2[2-9])\.'; then
        JAVA_CMD="java"
    fi
fi

if [ -z "$JAVA_CMD" ]; then
    echo "========================================================"
    echo "  [ERROR] Java 21+ (JDK 21) Runtime Not Found!"
    echo "========================================================"
    echo "  FormosaBearV requires Java 21 or higher to run."
    echo "  Please install and configure Java 21:"
    echo ""
    echo "  1. Download and install OpenJDK 21 or higher:"
    echo "     https://adoptium.net/temurin/releases/?version=21"
    echo ""
    echo "  2. Set JAVA_HOME environment variable to the installation directory,"
    echo "     or add java to system PATH."
    echo "========================================================"
    exit 1
fi

# 2. Port Configuration
PORT="${1:-8080}"
STOP_PORT="18080"
STOP_KEY="formosabearv"

# Check port occupation
if command -v lsof >/dev/null 2>&1; then
    if lsof -Pi :"$PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "[WARNING] Port $PORT is already in use by another process!"
        echo "You can pass another port, for example: ./start.sh 8088"
    fi
fi

# 3. Storage Directory
DOCS_HOME="${DOCS_HOME:-$DIR/data}"
mkdir -p "$DOCS_HOME"

echo "========================================================"
echo "  FormosaBearV (v2.03) Portable Server"
echo "========================================================"
echo "  Web URL:       http://localhost:$PORT/"
echo "  Data Path:     $DOCS_HOME"
echo "  Stop Command:  Run ./stop.sh or press Ctrl+C"
echo "========================================================"
echo "Starting server, please wait..."
echo ""

# Auto-initialize start.d if not present
if [ ! -d "$DIR/start.d" ] && [ ! -f "$DIR/start.ini" ]; then
    echo "[INFO] Initializing Jetty 12 base modules..."
    "$JAVA_CMD" -jar jetty-home/start.jar --create-startd --add-modules=ee10-deploy,http >/dev/null 2>&1 || true
fi

# Auto open browser
(
    sleep 3
    if command -v xdg-open >/dev/null 2>&1; then
        xdg-open "http://localhost:$PORT/" >/dev/null 2>&1 &
    elif command -v open >/dev/null 2>&1; then
        open "http://localhost:$PORT/" >/dev/null 2>&1 &
    fi
) &

exec "$JAVA_CMD" -Ddocs.home="$DOCS_HOME" -Djetty.http.port="$PORT" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --module=ee10-deploy,http
