plugins { alias(libs.plugins.kotlin.jvm) }
kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}
dependencies {
    api(project(":domain"))
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
tasks.test { useJUnitPlatform() }
