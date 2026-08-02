#!/usr/bin/env bash
# Qualia Code Desktop - macOS 打包脚本（jpackage app-image）
#
# 前置要求：
#   1. 已安装完整 JDK 17（含 jpackage），java/jpackage 在 PATH 中
#   2. 在 macOS 上执行（jpackage 不支持交叉打包；Apple Silicon 与 Intel 需各自打一份）
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
  --main-class com.lunarlanding.qualia.code.desktop.DesktopLauncher \
  --dest "$DIST_DIR" \
  --java-options "-XstartOnFirstThread" \
  --java-options "-Dfile.encoding=UTF-8" \
  "${ICON_ARGS[@]}"

echo ""
echo "==> 完成：$DIST_DIR/Qualia Code.app"
echo "   正式分发前需执行 codesign 签名与 notarytool 公证。"
