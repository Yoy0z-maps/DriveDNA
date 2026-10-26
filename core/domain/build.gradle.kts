plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Golden results: `./gradlew :core:domain:test -PupdateGolden=true` rewrites the file after an intended change.
    systemProperty("drivedna.goldenFile", file("src/test/resources/golden/replay-results.txt").absolutePath)
    systemProperty("drivedna.updateGolden", providers.gradleProperty("updateGolden").getOrElse("false"))
}
