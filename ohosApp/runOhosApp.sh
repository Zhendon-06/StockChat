#!/bin/sh

set -e

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
DEV_STUDIO_HOME=${DEV_STUDIO_HOME:-/Applications/DevEco-Studio.app/Contents}
REQUESTED_SDK_HOME=${DEVECO_SDK_HOME:-}
SDK_HOME=$DEV_STUDIO_HOME/sdk
if [ -n "$REQUESTED_SDK_HOME" ] && [ -d "$REQUESTED_SDK_HOME/default/openharmony/native/sysroot" ]; then
  SDK_HOME=$REQUESTED_SDK_HOME
fi
export DEVECO_SDK_HOME=$SDK_HOME
export PATH=$DEVECO_SDK_HOME:$DEV_STUDIO_HOME/jbr/Contents/Home/bin:$DEV_STUDIO_HOME/tools/node/bin:$DEV_STUDIO_HOME/tools/ohpm/bin:$DEV_STUDIO_HOME/tools/hvigor/bin:$PATH

echo "working path: $PROJECT_ROOT"

# Build the shared Kuikly/OpenHarmony library before packaging the HAP.  The
# current Harmony SDK exposes libc instead of a development-time
# libsqlite3.so; this compatibility link satisfies the inherited native
# linker flag while the OHOS demo uses in-memory chat data.
mkdir -p "$PROJECT_ROOT/.ohos-build"
if [ -f "$SDK_HOME/default/openharmony/native/sysroot/usr/lib/aarch64-linux-ohos/libc.so" ]; then
  ln -sfn "$SDK_HOME/default/openharmony/native/sysroot/usr/lib/aarch64-linux-ohos/libc.so" "$PROJECT_ROOT/.ohos-build/libsqlite3.so"
fi
(cd "$PROJECT_ROOT" && ./gradlew -c settings.ohos.gradle.kts :shared:linkReleaseSharedOhosArm64)
mkdir -p "$PROJECT_ROOT/ohosApp/entry/libs/arm64-v8a"
cp "$PROJECT_ROOT/shared/build/bin/ohosArm64/releaseShared/libshared.so" "$PROJECT_ROOT/ohosApp/entry/libs/arm64-v8a/libshared.so"

# The Kuikly Hvigor asset task reads this path from local.properties. Keep the
# default relative so a checkout can be built from any machine without
# committing a machine-specific local.properties file.
OHOS_LOCAL_PROPERTIES="$PROJECT_ROOT/ohosApp/local.properties"
if [ ! -f "$OHOS_LOCAL_PROPERTIES" ]; then
  cat > "$OHOS_LOCAL_PROPERTIES" <<'EOF'
kuikly.projectPath=../
kuikly.moduleName=shared
kuikly.ohosGradleSettings=settings.ohos.gradle.kts
kuikly.assetsPath=../shared/src/commonMain/assets
EOF
elif ! grep -q '^kuikly.assetsPath=' "$OHOS_LOCAL_PROPERTIES"; then
  printf '\nkuikly.assetsPath=../shared/src/commonMain/assets\n' >> "$OHOS_LOCAL_PROPERTIES"
fi

# Hvigor generates local debug configuration for both script and IDE builds.
cd "$PROJECT_ROOT/ohosApp"

$DEV_STUDIO_HOME/tools/ohpm/bin/ohpm install --all
$DEV_STUDIO_HOME/tools/node/bin/node $DEV_STUDIO_HOME/tools/hvigor/bin/hvigorw.js --sync -p product=default --analyze=normal --parallel
$DEV_STUDIO_HOME/tools/node/bin/node $DEV_STUDIO_HOME/tools/hvigor/bin/hvigorw.js --mode module -p module=entry@default -p product=default -p requiredDeviceType=phone assembleHap --analyze=normal --parallel

HDC_BIN=${HDC_BIN:-$SDK_HOME/default/openharmony/toolchains/hdc}
if [ ! -x "$HDC_BIN" ] && [ -x "$HOME/Library/OpenHarmony/Sdk/26.0.0/toolchains/hdc" ]; then
  HDC_BIN="$HOME/Library/OpenHarmony/Sdk/26.0.0/toolchains/hdc"
fi
targets=$($HDC_BIN list targets | tr -d '\r')
HAP_PATH=entry/build/default/outputs/default
if [ -e "$HAP_PATH/entry-default-unsigned.hap" ]; then
  debug_device_ids=""
  for target_id in $targets; do
    target_udid=$($HDC_BIN -t "$target_id" shell bm get -u 2>/dev/null | tail -n 1 | tr -d '\r' | sed -n 's/.* is :[[:space:]]*//p')
    if [ -n "$target_udid" ]; then
      debug_device_ids="$debug_device_ids $target_udid"
    fi
  done
  export OHOS_DEBUG_DEVICE_IDS=$(printf '%s\n' "$debug_device_ids" | awk '{$1=$1; print}')
  "$PROJECT_ROOT/ohosApp/signDebugHap.sh"
fi

case "$targets" in
  ""|"[Empty]")
  echo "error: 先启动鸿蒙模拟器或连接真机"
  exit 1
  ;;
  *)
  for target_id in $($HDC_BIN list targets); do
  echo "install to $target_id"
  $HDC_BIN -t "$target_id" shell aa force-stop com.guet.liang.stockchat
  $HDC_BIN -t "$target_id" install entry/build/default/outputs/default/entry-default-signed.hap
  $HDC_BIN -t "$target_id" shell aa start -a EntryAbility -b com.guet.liang.stockchat
  done
  ;;
esac
