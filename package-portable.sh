#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

APP_VERSION=$(grep -m 1 -o '<version>[^<]*</version>' "$DIR/pom.xml" | sed 's/<[^>]*>//g' | tr -d '[:space:]')
if [ -z "$APP_VERSION" ]; then
    echo "[ERROR] 無法從 pom.xml 讀取專案版本號！"
    exit 1
fi

echo "=========================================================="
echo "  FormosaBearV (v${APP_VERSION}) Portable Package Builder"
echo "=========================================================="

JETTY_DIR="$DIR/tools/jetty"
WAR_FILE="$DIR/docs-web/target/docs-web-${APP_VERSION}.war"
OUTPUT_DIR="$DIR/target"
OUTPUT_ZIP="$OUTPUT_DIR/FormosaBearV-docs-${APP_VERSION}-portable.zip"
STAGING_ROOT="$OUTPUT_DIR/portable-staging"
BUNDLE_DIR="$STAGING_ROOT/FormosaBearV-docs-${APP_VERSION}-portable"

# 1. Resolve highest-version local Jetty ZIP or download latest from jetty.org / Maven Central
mkdir -p "$JETTY_DIR"
JETTY_ZIP=$(find "$JETTY_DIR" -maxdepth 1 -type f -name "jetty-home-*.zip" -size +0c 2>/dev/null | sort -V | tail -n 1 || true)

if [ -z "$JETTY_ZIP" ] || [ ! -f "$JETTY_ZIP" ]; then
    echo "[1/5] $JETTY_DIR 內無現有 Jetty ZIP，正在從 https://jetty.org/download.html 查詢最新版本..."
    LATEST_VER=$(curl -fsSL --max-time 15 "https://jetty.org/download.html" 2>/dev/null | grep -oE 'jetty-home-12\.[0-9]+\.[0-9]+\.zip' | sed -E 's/jetty-home-(.*)\.zip/\1/' | sort -V | tail -n 1 || true)
    if [ -z "$LATEST_VER" ]; then
        echo "       改從 Maven Central metadata 查詢最新 Jetty 12 版本..."
        LATEST_VER=$(curl -fsSL --max-time 15 "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/maven-metadata.xml" 2>/dev/null | grep -oE '<version>12\.[0-9]+\.[0-9]+</version>' | sed -E 's/<\/?version>//g' | sort -V | tail -n 1 || true)
    fi
    if [ -z "$LATEST_VER" ]; then
        echo "[ERROR] 無法從 https://jetty.org/download.html 或 Maven Central 取得最新 Jetty 12 版本號！"
        exit 1
    fi
    JETTY_ZIP="$JETTY_DIR/jetty-home-${LATEST_VER}.zip"
    DOWNLOAD_URL="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${LATEST_VER}/jetty-home-${LATEST_VER}.zip"
    echo "       正在下載 jetty-home-${LATEST_VER}.zip ($DOWNLOAD_URL)..."
    if curl -fL --retry 2 -o "$JETTY_ZIP" "$DOWNLOAD_URL"; then
        echo "       已成功下載並快取基底 Jetty ZIP 至 $JETTY_ZIP"
    else
        rm -f "$JETTY_ZIP"
        mvn dependency:get "-Dartifact=org.eclipse.jetty:jetty-home:${LATEST_VER}:zip" -Dtransitive=false
        M2_ZIP="$HOME/.m2/repository/org/eclipse/jetty/jetty-home/${LATEST_VER}/jetty-home-${LATEST_VER}.zip"
        if [ -f "$M2_ZIP" ]; then
            cp "$M2_ZIP" "$JETTY_ZIP"
            echo "       已成功快取基底 Jetty ZIP 至 $JETTY_ZIP"
        else
            echo "[ERROR] 下載 jetty-home-${LATEST_VER}.zip 失敗！"
            exit 1
        fi
    fi
else
    echo "[1/5] 使用最高版本基底 Jetty ZIP: $JETTY_ZIP"
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
    chmod +x "$BUNDLE_DIR/start.sh" "$BUNDLE_DIR/stop.sh" "$BUNDLE_DIR/demo.sh" 2>/dev/null || true
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
UNZIPPED_TARGET="$OUTPUT_DIR/FormosaBearV-docs-${APP_VERSION}-portable"
rm -rf "$UNZIPPED_TARGET"
mkdir -p "$UNZIPPED_TARGET"
cp -r "$BUNDLE_DIR"/* "$UNZIPPED_TARGET"/

rm -rf "$STAGING_ROOT"

echo "=========================================================="
echo "  [成功] 綠色可攜版打包完成！"
echo "  輸出檔案: $OUTPUT_ZIP"
echo "=========================================================="
