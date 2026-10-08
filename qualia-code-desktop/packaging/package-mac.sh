#!/usr/bin/env bash
# Qualia Code Desktop - macOS 打包脚本（jpackage app-image）
#
# 前置要求：
#   1. 已安装完整 JDK 17（含 jpackage），java/jpackage 在 PATH 中
#   2. 已安装 Node.js（>= 20.19 或 >= 22.12），用于构建 qualia-code-ui 前端
#   3. 在 macOS 上执行（jpackage 不支持交叉打包；Apple Silicon 与 Intel 需各自打一份）
#
# 关键点：
#   - SWT 在 macOS 上必须以 -XstartOnFirstThread 运行，否则 SWT 事件循环无法在主线程启动
#   - 正式分发需 Apple 开发者证书做签名 + 公证（notarization），此处仅生成未签名 app-image
#
# 用法（在项目根目录执行）：
#   ./qualia-code-desktop/packaging/package-mac.sh
#
# 产物：qualia-code-desktop/target/dist/Qualia Code.app

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODULE_DIR="$PROJECT_ROOT/qualia-code-desktop"
TARGET_DIR="$MODULE_DIR/target"
DIST_DIR="$TARGET_DIR/dist"
VERSION="1.5.0"

echo "==> 构建可执行 jar（含全部依赖）..."
cd "$PROJECT_ROOT"
./mvnw -q -pl qualia-code-desktop -am clean package -DskipTests || mvn -q -pl qualia-code-desktop -am clean package -DskipTests

# 构建前端：npm run build 产物直写 qualia-code/target/classes/static（vite outDir 约定）。
# 必须在 clean 之后执行，否则产物会被 clean 删除。
UI_DIR="$PROJECT_ROOT/qualia-code-ui"
if [ ! -d "$UI_DIR/node_modules" ]; then
  echo "==> 安装前端依赖 (npm install)..."
  (cd "$UI_DIR" && npm install --no-fund --no-audit)
fi
echo "==> 构建前端 (npm run build)..."
(cd "$UI_DIR" && npm run build)

# 无 clean 增量重打：static 已在 target/classes 中，重打 qualia-code jar 并重新 shade 进 uber-jar
echo "==> 重新打包（含前端静态资源，不执行 clean）..."
./mvnw -q -pl qualia-code-desktop -am package -DskipTests || mvn -q -pl qualia-code-desktop -am package -DskipTests

# 定位 shaded jar
JAR="$(find "$TARGET_DIR" -maxdepth 1 -name 'qualia-code-desktop-*.jar' ! -name '*original*' | head -n 1)"
if [ -z "$JAR" ]; then echo "未找到可执行 jar"; exit 1; fi
JAR_NAME="$(basename "$JAR")"
echo "==> 使用 jar: $JAR_NAME"

# 准备 input 目录
INPUT_DIR="$TARGET_DIR/jpackage-input"
rm -rf "$INPUT_DIR" && mkdir -p "$INPUT_DIR"
cp "$JAR" "$INPUT_DIR/"

rm -rf "$DIST_DIR" && mkdir -p "$DIST_DIR"

ICON_ARGS=()
if [ -f "$SCRIPT_DIR/app.icns" ]; then
  ICON_ARGS=(--icon "$SCRIPT_DIR/app.icns")
fi

echo "==> 运行 jpackage 生成 app-image..."
jpackage \
  --type app-image \
  --name "Qualia Code" \
  --app-version "$VERSION" \
  --input "$INPUT_DIR" \
  --main-jar "$JAR_NAME" \
  --main-class cn.lunarlanding.qualia.code.desktop.DesktopLauncher \
  --dest "$DIST_DIR" \
  --java-options "-XstartOnFirstThread" \
  --java-options "-Dfile.encoding=UTF-8" \
  "${ICON_ARGS[@]}"

echo ""
echo "==> 完成：$DIST_DIR/Qualia Code.app"
echo "   正式分发前需执行 codesign 签名与 notarytool 公证。"
