#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${NEXUS_SDK_PACKAGE:?Set to an xtask-packaged x86_64-unknown-linux-gnu default library from the same SDK source}"
export LD_LIBRARY_PATH="$NEXUS_SDK_PACKAGE${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo build --release --locked
mkdir -p build/jvm
javac -d build/jvm tools/jvm/org/nexus/lab/Native.java
native_target="${CARGO_TARGET_DIR:-target}"
java -cp build/jvm org.nexus.lab.Native "$NEXUS_SDK_PACKAGE/libnexus_sdk.so" "$native_target/release/libnexus_lab.so"
