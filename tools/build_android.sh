#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${ANDROID_NDK_HOME:=$ANDROID_HOME/ndk/27.2.12479018}"
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=aarch64-linux-android26-clang
export NEXUS_SDK_PACKAGE="$PWD/$(python3 -c 'import json;print(json.load(open("SDK_IDENTITY.json"))["package_directory"])')"
profile="$(python3 -c 'import json;print(json.load(open("SDK_IDENTITY.json"))["profile"])')"
features=(); gradle_options=()
if [[ "$profile" == staging-enrollment ]]; then features=(--features staging); gradle_options=(-PnexusStaging=true); fi
python3 tools/collect_notices.py
cargo build --release --locked --target aarch64-linux-android "${features[@]}"
python3 - <<'PY'
import json,hashlib,os,shutil
from pathlib import Path
r=Path.cwd();p=r/'app/src/main/jniLibs/arm64-v8a';p.mkdir(parents=True,exist_ok=True)
source=Path(os.environ.get('CARGO_TARGET_DIR','target'))/'aarch64-linux-android/release/libnexus_lab.so'
shutil.copy2(source,p/'libnexus_lab.so');shutil.copy2(Path(os.environ['NEXUS_SDK_PACKAGE'])/'libnexus_sdk.so',p/'libnexus_sdk.so')
x=json.loads((r/'SDK_IDENTITY.json').read_text());assert hashlib.sha256((p/'libnexus_sdk.so').read_bytes()).hexdigest()==x['sdk_sha256']
x['bridge_sha256']=hashlib.sha256(source.read_bytes()).hexdigest()
a=r/'app/src/main/assets';a.mkdir(parents=True,exist_ok=True);(a/'sdk-identity.json').write_text(json.dumps(x,indent=2)+'\n')
PY
./gradlew --no-daemon "${gradle_options[@]}" :app:testReleaseUnitTest :app:assembleRelease :app:assembleDebug :app:assembleReleaseAndroidTest
