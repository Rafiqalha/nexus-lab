#!/usr/bin/env bash
set -euo pipefail
lab="$(cd "$(dirname "$0")/.." && pwd)"
sdk="$(realpath "${1:?Usage: tools/prepare_sdk.sh SDK_CHECKOUT NEW_PACKAGE_OUTPUT}")"
out="${2:?Use a fresh SDK-relative dist/NAME}"
sha="$(git -C "$sdk" rev-parse HEAD)"
[[ -z "$(git -C "$sdk" status --porcelain)" ]] || { echo 'SDK must have source-bound validation for its current tree' >&2; exit 1; }
: "${ANDROID_HOME:?Set Android SDK root}"
: "${ANDROID_NDK_HOME:=$ANDROID_HOME/ndk/27.2.12479018}"
export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=aarch64-linux-android26-clang
(cd "$sdk" && cargo run -p xtask --locked -- package --target aarch64-linux-android --output "$out")
python3 "$lab/tools/import_sdk.py" "$sdk/$out" "$sha"
