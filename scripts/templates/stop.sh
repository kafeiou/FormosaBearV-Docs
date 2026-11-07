#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

JAVA_CMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

STOP_PORT="${1:-18080}"
STOP_KEY="formosabearv"

echo "正在安全關閉 FormosaBearV 伺服器 (埠號: $STOP_PORT)..."
"$JAVA_CMD" -DSTOP.PORT="$STOP_PORT" -DSTOP.KEY="$STOP_KEY" -jar jetty-home/start.jar --stop || true
echo "[OK] 關閉指令已送出。"
