#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 1. Check for Java 21+ Runtime (from JAVA_HOME or PATH)
JAVA_CMD=""

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    if "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "(21|2[2-9]|[3-9][0-9])\.'; then
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi
fi

if [ -z "$JAVA_CMD" ] && command -v java >/dev/null 2>&1; then
    if java -version 2>&1 | grep -qE 'version "(21|2[2-9]|[3-9][0-9])\.'; then
        JAVA_CMD="java"
    fi
fi

if [ -z "$JAVA_CMD" ]; then
    echo "========================================================"
    echo "  [ERROR] Java 21+ (JDK 21) Runtime Not Found!"
    echo "========================================================"
    echo "  FormosaBearV requires Java 21 or higher to run."
    exit 1
fi

# 2. Port & Reset Interval Configuration
PORT="${1:-8080}"
RESET_HOURS="${2:-24}"
RESET_SECONDS="${RESET_SECONDS:-$((RESET_HOURS * 3600))}"
STOP_PORT="18080"
STOP_KEY="formosabearv"

# 3. Storage & Backup Directory
DOCS_HOME="${DOCS_HOME:-$DIR/data}"
BACKUP_DIR="$DIR/data.bak"

if [ ! -f "$DIR/jetty-home/start.jar" ]; then
    echo "[ERROR] jetty-home/start.jar not found in $DIR"
    exit 1
fi

if [ ! -d "$DIR/start.d" ] && [ ! -f "$DIR/start.ini" ]; then
    echo "[INFO] Initializing Jetty 12 base modules..."
    "$JAVA_CMD" -jar jetty-home/start.jar --create-startd --add-modules=ee10-deploy,http >/dev/null 2>&1 || true
fi

JETTY_PID=""

stop_jetty() {
    "$JAVA_CMD" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --stop --stop-wait=30 >/dev/null 2>&1 || true
    if [ -n "$JETTY_PID" ] && kill -0 "$JETTY_PID" 2>/dev/null; then
        for _ in $(seq 1 30); do
            if ! kill -0 "$JETTY_PID" 2>/dev/null; then
                break
            fi
            sleep 1
        done
        if kill -0 "$JETTY_PID" 2>/dev/null; then
            echo "[WARN] Jetty did not stop in time; sending SIGTERM..."
            kill "$JETTY_PID" 2>/dev/null || true
            for _ in $(seq 1 10); do
                if ! kill -0 "$JETTY_PID" 2>/dev/null; then
                    break
                fi
                sleep 1
            done
        fi
        if kill -0 "$JETTY_PID" 2>/dev/null; then
            echo "[WARN] Jetty still alive after SIGTERM; sending SIGKILL..."
            kill -9 "$JETTY_PID" 2>/dev/null || true
        fi
        wait "$JETTY_PID" 2>/dev/null || true
    fi
    JETTY_PID=""
}

cleanup() {
    echo ""
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Shutting down FormosaBearV Demo Server..."
    stop_jetty
    exit 0
}

trap cleanup INT TERM

echo "========================================================"
echo "  FormosaBearV Portable Demo Server (Auto-Reset)"
echo "========================================================"
echo "  Web URL:        http://localhost:$PORT/"
echo "  Data Path:      $DOCS_HOME"
echo "  Baseline Path:  $BACKUP_DIR"
echo "  Reset Interval: ${RESET_HOURS} hour(s) (${RESET_SECONDS}s)"
echo "  Stop Command:   Press Ctrl+C"
echo "========================================================"
echo ""

while true; do
    if [ ! -d "$BACKUP_DIR" ]; then
        echo "[WARNING] Baseline directory 'data.bak' was not found!"
        echo "          Please manually copy 'data' to 'data.bak' to enable automatic reset/restart."
        if [ -z "$JETTY_PID" ] || ! kill -0 "$JETTY_PID" 2>/dev/null; then
            echo "          Starting server normally using 'data' (will check again at the next interval)..."
            mkdir -p "$DOCS_HOME"
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting FormosaBearV Demo Server on port $PORT..."
            "$JAVA_CMD" -Ddocs.home="$DOCS_HOME" -Djetty.http.port="$PORT" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --module=ee10-deploy,http &
            JETTY_PID=$!
        else
            echo "          Keeping current server running without resetting 'data' (will check again at the next interval)..."
        fi
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Server running (PID $JETTY_PID). Next check in ${RESET_SECONDS}s..."
        ELAPSED=0
        while [ "$ELAPSED" -lt "$RESET_SECONDS" ]; do
            if ! kill -0 "$JETTY_PID" 2>/dev/null; then
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] Jetty exited unexpectedly; restarting..."
                JETTY_PID=""
                break
            fi
            sleep 5
            ELAPSED=$((ELAPSED + 5))
        done
        echo ""
        continue
    fi

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Stopping any running Jetty instance..."
    stop_jetty
    sleep 2

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Resetting '$DOCS_HOME' from '$BACKUP_DIR'..."
    rm -rf "$DOCS_HOME"
    cp -a "$BACKUP_DIR" "$DOCS_HOME"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Demo data restored successfully."

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting FormosaBearV Demo Server on port $PORT..."
    "$JAVA_CMD" -Ddocs.home="$DOCS_HOME" -Djetty.http.port="$PORT" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --module=ee10-deploy,http &
    JETTY_PID=$!

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Server running (PID $JETTY_PID). Next automatic reset in ${RESET_SECONDS}s..."
    ELAPSED=0
    while [ "$ELAPSED" -lt "$RESET_SECONDS" ]; do
        if ! kill -0 "$JETTY_PID" 2>/dev/null; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN] Jetty exited unexpectedly; restarting..."
            JETTY_PID=""
            break
        fi
        sleep 5
        ELAPSED=$((ELAPSED + 5))
    done

    echo ""
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Reset interval reached. Restarting and resetting demo data..."
done
