#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

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
    echo "[ERROR] Java 21+ runtime not found. Please set JAVA_HOME to JDK 21+ or add java to PATH."
    exit 1
fi

STOP_PORT="${1:-18080}"
STOP_KEY="formosabearv"

echo "Stopping FormosaBearV Server on port $STOP_PORT..."
"$JAVA_CMD" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --stop || true
echo "[OK] Stop signal sent."
