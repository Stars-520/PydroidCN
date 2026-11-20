#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  PydroidCN —— Pydroid 3 汉化 LSPosed 模块 一键构建
#  用法： bash build.sh [输出APK名]
# ═══════════════════════════════════════════════════════════
set -u
SRC_ROOT="/storage/emulated/0/java66666666/PydroidCN"
WORK="/root/pydroidcn"
OUT="${1:-PydroidCN.apk}"
BT=/root/sdk/build-tools/34.0.0
AJ=/root/p34/android-34/android.jar
[ -f "$AJ" ] || AJ=/storage/emulated/0/java66666666/android.jar
AAPT2=/usr/local/bin/aapt2
[ -x "$AAPT2" ] || AAPT2="$BT/aapt2"
PKG_PATH="com/hsqs/pydroidcn"

mkdir -p "$WORK"
cp -f "$SRC_ROOT/pack.py" "$WORK/pack.py" 2>/dev/null
cd "$WORK" || exit 1

echo "════════════════════════════════════"
echo " PydroidCN 构建开始"
echo "  aapt2 = $AAPT2"
echo "  android.jar = $AJ"
echo "════════════════════════════════════"

rm -rf build
mkdir -p build/classes build/gen

# ── 0. Xposed API（compileOnly） ──
API_JAR="$WORK/XposedBridgeApi-82.jar"
if [ ! -s "$API_JAR" ] || [ "$(stat -c%s "$API_JAR")" -lt 10000 ]; then
  echo "[0/6] 下载 Xposed API…"
  for U in \
    "https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar" \
    "https://maven.aliyun.com/repository/central/de/robv/android/xposed/api/82/api-82.jar" \
    "https://repo.huaweicloud.com/repository/maven/de/robv/android/xposed/api/82/api-82.jar" \
    "https://repo1.maven.org/maven2/de/robv/android/xposed/api/82/api-82.jar" ; do
    curl -sL -m 60 -o "$API_JAR" "$U"
    if [ -s "$API_JAR" ] && [ "$(stat -c%s "$API_JAR")" -gt 10000 ]; then
      echo "  来自 $U"
      break
    fi
  done
fi
if [ ! -s "$API_JAR" ] || [ "$(stat -c%s "$API_JAR")" -lt 10000 ]; then
  echo "✗ Xposed API 缺失，请手动放到 $API_JAR"
  exit 1
fi
echo "✓ Xposed API: $(stat -c%s "$API_JAR") bytes"

# ── 1. 编译资源 ──
echo "[1/6] 编译资源…"
$AAPT2 compile --dir "$SRC_ROOT/app/res" -o build/res.zip || exit 1

# ── 2. 链接资源 ──
echo "[2/6] 链接资源…"
$AAPT2 link -o build/app-unsigned.apk -I "$AJ" \
  --manifest "$SRC_ROOT/app/AndroidManifest.xml" \
  --java build/gen build/res.zip \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0 || exit 1

# ── 3. 编译 Java ──
echo "[3/6] 编译 Java…"
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$AJ" -cp "$AJ:$API_JAR" \
  -d build/classes \
  $(find "$SRC_ROOT/app/src" -name '*.java') \
  $(find build/gen -name '*.java') 2>&1 \
  | grep -v 'bootstrap class path' | grep -v 'source value' \
  | grep -v 'target value' | grep -v 'deprecat' || true
if [ ! -f "build/classes/$PKG_PATH/MainHook.class" ]; then
  echo "✗ 编译失败：未生成 MainHook.class"
  exit 1
fi
echo "✓ Java 编译通过"

# ── 4. DEX ──
echo "[4/6] 生成 DEX…"
$BT/d8 --min-api 24 --lib "$AJ" --output build/ \
  $(find build/classes -name '*.class') || exit 1

# ── 5. 塞 xposed_init + 打包对齐 ──
echo "[5/6] 打包 + 对齐…"
python3 - <<'PYEOF' || exit 1
import zipfile, os
src = 'build/app-unsigned.apk'
tmp = 'build/base2.apk'
init = '/storage/emulated/0/java66666666/PydroidCN/app/assets/xposed_init'
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(tmp, 'w') as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    with open(init, 'rb') as f:
        zout.writestr('assets/xposed_init', f.read())
os.replace(tmp, src)
print('  xposed_init 已写入')
PYEOF
python3 pack.py || exit 1

# ── 6. 签名 ──
echo "[6/6] 签名…"
KS="$WORK/stars.keystore"
if [ ! -f "$KS" ]; then
  echo "生成签名密钥…"
  keytool -genkeypair -keystore "$KS" -alias stars \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass 123456 -keypass 123456 \
    -dname "CN=Stars, OU=Dev, O=Stars, L=CN, ST=CN, C=CN" 2>/dev/null
fi
java -jar $BT/lib/apksigner.jar sign \
  --ks "$KS" --ks-key-alias stars \
  --ks-pass pass:123456 --key-pass pass:123456 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "build/$OUT" build/aligned.apk || exit 1
java -jar $BT/lib/apksigner.jar verify "build/$OUT" && echo "✓ 签名有效"

DEST="/storage/emulated/0/java66666666/$OUT"
cp -f "build/$OUT" "$DEST" 2>/dev/null && echo "✓ 已输出到 $DEST"
ls -l "$DEST"
echo "════════════════════════════════════"
echo " 构建完成：$OUT"
echo "════════════════════════════════════"