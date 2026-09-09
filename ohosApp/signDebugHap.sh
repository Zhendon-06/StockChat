#!/bin/sh

set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
SDK_HOME=${DEVECO_SDK_HOME:-/Applications/DevEco-Studio.app/Contents/sdk}
OHOS_HOME="$SDK_HOME/default/openharmony"
TOOLCHAIN_LIB="$OHOS_HOME/toolchains/lib"
if [ ! -f "$TOOLCHAIN_LIB/hap-sign-tool.jar" ]; then
  TOOLCHAIN_LIB="${OPENHARMONY_SDK_HOME:-$HOME/Library/OpenHarmony/Sdk/26.0.0}/toolchains/lib"
fi

SIGN_DIR="$PROJECT_ROOT/.ohos-build/signing"
HAP_DIR="$PROJECT_ROOT/ohosApp/entry/build/default/outputs/default"
KEYSTORE="$TOOLCHAIN_LIB/OpenHarmony.p12"
SIGN_TOOL="$TOOLCHAIN_LIB/hap-sign-tool.jar"
DEBUG_TEMPLATE="$TOOLCHAIN_LIB/UnsgnedDebugProfileTemplate.json"

if [ ! -f "$KEYSTORE" ] || [ ! -f "$SIGN_TOOL" ] || [ ! -f "$DEBUG_TEMPLATE" ]; then
  echo "error: DevEco/OpenHarmony SDK signing tools are unavailable" >&2
  exit 1
fi

mkdir -p "$SIGN_DIR"

ensure_keypair() {
  alias_name=$1
  if ! keytool -list -keystore "$SIGN_DIR/local.p12" -storepass 123456 -alias "$alias_name" >/dev/null 2>&1; then
    java -jar "$SIGN_TOOL" generate-keypair \
      -keyAlias "$alias_name" -keyPwd 123456 -keyAlg ECC -keySize NIST-P-256 \
      -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 >/dev/null
  fi
}

ensure_keypair profile-ca-key
ensure_keypair profile-key
ensure_keypair app-key

keytool -exportcert -rfc -alias 'openharmony application root ca' \
  -keystore "$KEYSTORE" -storepass 123456 -file "$SIGN_DIR/root-ca.cer" >/dev/null
keytool -exportcert -rfc -alias 'openharmony application ca' \
  -keystore "$KEYSTORE" -storepass 123456 -file "$SIGN_DIR/app-ca-leaf.cer" >/dev/null
cat "$SIGN_DIR/app-ca-leaf.cer" "$SIGN_DIR/root-ca.cer" > "$SIGN_DIR/app-ca.cer"

java -jar "$SIGN_TOOL" generate-ca \
  -keyAlias profile-ca-key -keyPwd 123456 -keyAlg ECC -keySize NIST-P-256 \
  -issuer 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=OpenHarmony Application Root CA' \
  -issuerKeyAlias 'openharmony application root ca' -issuerKeyPwd 123456 \
  -subject 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=StockChat Profile CA' \
  -validity 3650 -signAlg SHA256withECDSA -basicConstraintsPathLen 0 \
  -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 \
  -issuerKeystoreFile "$KEYSTORE" -issuerKeystorePwd 123456 \
  -outFile "$SIGN_DIR/profile-ca.cer" >/dev/null

java -jar "$SIGN_TOOL" generate-profile-cert \
  -keyAlias profile-key -keyPwd 123456 \
  -issuer 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=StockChat Profile CA' \
  -issuerKeyAlias profile-ca-key -issuerKeyPwd 123456 \
  -subject 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=StockChat Profile Debug' \
  -validity 365 -signAlg SHA256withECDSA \
  -rootCaCertFile "$SIGN_DIR/root-ca.cer" -subCaCertFile "$SIGN_DIR/profile-ca.cer" \
  -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 \
  -issuerKeystoreFile "$SIGN_DIR/local.p12" -issuerKeystorePwd 123456 \
  -outForm certChain -outFile "$SIGN_DIR/profile-cert.cer" >/dev/null

cp "$DEBUG_TEMPLATE" "$SIGN_DIR/profile.json"
python3 - "$SIGN_DIR/profile.json" <<'PY'
import json
import os
import sys
import time

path = sys.argv[1]
with open(path, encoding="utf-8") as source:
    profile = json.load(source)
now = int(time.time())
profile["version-name"] = "1.0.0"
profile["version-code"] = 1000000
profile["validity"] = {"not-before": now - 86400, "not-after": now + 365 * 86400}
profile["bundle-info"]["bundle-name"] = "com.guet.liang.stockchat"
device_ids = [item for item in os.environ.get("OHOS_DEBUG_DEVICE_IDS", "").split() if item]
if device_ids:
    profile.setdefault("debug-info", {})["device-ids"] = device_ids
with open(path, "w", encoding="utf-8") as target:
    json.dump(profile, target, indent=2)
PY

java -jar "$SIGN_TOOL" sign-profile \
  -mode localSign -keyAlias profile-key -keyPwd 123456 \
  -profileCertFile "$SIGN_DIR/profile-cert.cer" -inFile "$SIGN_DIR/profile.json" \
  -signAlg SHA256withECDSA -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 \
  -outFile "$SIGN_DIR/profile.p7b" >/dev/null

java -jar "$SIGN_TOOL" generate-app-cert \
  -keyAlias app-key -keyPwd 123456 \
  -issuer 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=OpenHarmony Application CA' \
  -issuerKeyAlias 'openharmony application ca' -issuerKeyPwd 123456 \
  -subject 'C=CN,O=OpenHarmony,OU=OpenHarmony Team,CN=StockChat Debug' \
  -validity 365 -signAlg SHA256withECDSA \
  -rootCaCertFile "$SIGN_DIR/root-ca.cer" -subCaCertFile "$SIGN_DIR/app-ca.cer" \
  -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 \
  -issuerKeystoreFile "$KEYSTORE" -issuerKeystorePwd 123456 \
  -outForm certChain -outFile "$SIGN_DIR/app-cert.cer" >/dev/null

java -jar "$SIGN_TOOL" sign-app \
  -mode localSign -keyAlias app-key -keyPwd 123456 \
  -appCertFile "$SIGN_DIR/app-cert.cer" -profileFile "$SIGN_DIR/profile.p7b" \
  -inFile "$HAP_DIR/entry-default-unsigned.hap" -signAlg SHA256withECDSA \
  -keystoreFile "$SIGN_DIR/local.p12" -keystorePwd 123456 \
  -outFile "$HAP_DIR/entry-default-signed.hap" -compatibleVersion 12 -signCode 1 >/dev/null

echo "signed HAP: $HAP_DIR/entry-default-signed.hap"
