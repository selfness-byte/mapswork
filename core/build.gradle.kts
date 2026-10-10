plugins {
    kotlin("jvm")
}
kotlin {
    jvmToolchain(21)
}
dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    testLogging {
        events("failed", "skipped")
    }
}
