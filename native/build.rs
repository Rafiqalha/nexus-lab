fn main() {
    println!("cargo:rerun-if-env-changed=NEXUS_SDK_PACKAGE");
    let package =
        std::env::var("NEXUS_SDK_PACKAGE").expect("set NEXUS_SDK_PACKAGE to the imported package");
    assert!(std::path::Path::new(&package)
        .join("libnexus_sdk.so")
        .is_file());
    println!("cargo:rustc-link-search=native={package}");
    println!("cargo:rustc-link-lib=dylib=nexus_sdk");
}
