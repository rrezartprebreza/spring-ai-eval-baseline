plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0"))
    implementation("org.springframework.ai:spring-ai-model")

    testImplementation("io.github.rrezartprebreza:eval-junit:0.2.0")
    testImplementation("io.github.rrezartprebreza:eval-spring-ai:0.2.0")
    testImplementation("org.springframework.ai:spring-ai-openai") // only used when recording
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}

tasks.test {
    useJUnitPlatform()
    // Pass -Deval.mode=record and -Deval.update-baseline=true on to the test JVM.
    listOf("eval.mode", "eval.update-baseline").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
