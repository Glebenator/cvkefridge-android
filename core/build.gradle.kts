plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The tests read the shared specification in the spec folder of the repository.
tasks.test {
    val specDir = rootProject.layout.projectDirectory.dir("spec")
    inputs.dir(specDir)
    systemProperty("spec.dir", specDir.asFile.absolutePath)
}
