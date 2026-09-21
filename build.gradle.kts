// Declared here, applied nowhere, so every module resolves the same plugin versions from one
// classpath. Without this the Android modules fall back to the Kotlin that AGP 9 bundles, which is
// 2.2.10, while the JVM modules compile at 2.4.20 and emit metadata 2.2.10 cannot read. The :app
// module happened to escape that only because the Compose plugin drags the Kotlin plugin up with
// it; :persistence had nothing doing the same and failed on the metadata version.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}
