#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=========================================================="
echo "  FormosaBearV (v2.03) Portable Package Builder"
echo "=========================================================="

JETTY_ZIP="$DIR/tools/jetty/jetty-home-12.1.14.zip"
WAR_FILE="$DIR/docs-web/target/docs-web-2.03.war"
OUTPUT_DIR="$DIR/target"
OUTPUT_ZIP="$OUTPUT_DIR/FormosaBearV-docs-2.03-portable.zip"
STAGING_ROOT="$OUTPUT_DIR/portable-staging"
BUNDLE_DIR="$STAGING_ROOT/FormosaBearV-docs-2.03-portable"

# 1. Base Jetty ZIP
mkdir -p "$DIR/tools/jetty"
if [ ! -f "$JETTY_ZIP" ]; then
    echo "[1/5] 找不到 $JETTY_ZIP，正在從 Maven 中央倉庫下載..."
    mvn dependency:get "-Dartifact=org.eclipse.jetty:jetty-home:12.1.14:zip" -Dtransitive=false
    M2_ZIP="$HOME/.m2/repository/org/eclipse/jetty/jetty-home/12.1.14/jetty-home-12.1.14.zip"
    if [ -f "$M2_ZIP" ]; then
        cp "$M2_ZIP" "$JETTY_ZIP"
        echo "       已成功快取基底 Jetty ZIP 至 $JETTY_ZIP"
    else
        echo "[ERROR] 下載 jetty-home 失敗！"
        exit 1
    fi
else
    echo "[1/5] 使用專案基底 Jetty ZIP: $JETTY_ZIP"
fi

# 2. Check WAR
if [ ! -f "$WAR_FILE" ]; then
    echo "[2/5] 找不到 $WAR_FILE，正在執行 Maven 編譯..."
    mvn clean package -DskipTests
    if [ ! -f "$WAR_FILE" ]; then
        echo "[ERROR] Maven 編譯完成但未產出 $WAR_FILE！"
        exit 1
    fi
else
    echo "[2/5] 使用現有 WAR: $WAR_FILE"
fi

# 3. Assemble
rm -rf "$STAGING_ROOT"
mkdir -p "$BUNDLE_DIR"

echo "[3/5] 解壓縮基底 Jetty 12 伺服器..."
TEMP_JETTY="$STAGING_ROOT/temp-jetty"
mkdir -p "$TEMP_JETTY"
unzip -q "$JETTY_ZIP" -d "$TEMP_JETTY"
EXTRACTED_HOME=$(find "$TEMP_JETTY" -mindepth 1 -maxdepth 1 -type d | head -n 1)
mv "$EXTRACTED_HOME" "$BUNDLE_DIR/jetty-home"
rm -rf "$TEMP_JETTY"

echo "[4/5] 初始化 Jetty 模組與佈署應用程式..."
(
    cd "$BUNDLE_DIR"
    JAVA_BIN="java"
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_BIN="$JAVA_HOME/bin/java"
    fi
    "$JAVA_BIN" -jar "jetty-home/start.jar" --create-startd --add-modules=ee10-deploy,http >/dev/null 2>&1 || true
)

mkdir -p "$BUNDLE_DIR/webapps"
cp "$WAR_FILE" "$BUNDLE_DIR/webapps/ROOT.war"
mkdir -p "$BUNDLE_DIR/data"

# Copy runtime scripts and README from templates
if [ -d "$DIR/scripts/templates" ]; then
    cp "$DIR/scripts/templates/"* "$BUNDLE_DIR/"
    chmod +x "$BUNDLE_DIR/start.sh" "$BUNDLE_DIR/stop.sh" 2>/dev/null || true
fi

# 5. Compress
echo "[5/5] 正在壓縮可攜版 ZIP..."
mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_ZIP"
(
    cd "$BUNDLE_DIR"
    zip -q -r "$OUTPUT_ZIP" .
)

# Also sync to unzipped directory in output directory for immediate local use
UNZIPPED_TARGET="$OUTPUT_DIR/FormosaBearV-docs-2.03-portable"
rm -rf "$UNZIPPED_TARGET"
mkdir -p "$UNZIPPED_TARGET"
cp -r "$BUNDLE_DIR"/* "$UNZIPPED_TARGET"/

rm -rf "$STAGING_ROOT"

echo "=========================================================="
echo "  [成功] 綠色可攜版打包完成！"
echo "  輸出檔案: $OUTPUT_ZIP"
echo "=========================================================="
