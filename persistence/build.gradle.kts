plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zynergy.forager.persistence"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

}

dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}

// Schemas are written straight into the instrumentation test's assets, and committed, so
// MigrationTestHelper reads the real recorded shape of each past version. Putting them here rather
// than in a neutral directory avoids AGP 9's source-set assets API, which rejects the usual
// srcDir call with a ClassCastException.
ksp { arg("room.schemaLocation", "$projectDir/src/androidTest/assets") }
