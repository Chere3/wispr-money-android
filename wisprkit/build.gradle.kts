// Cliente de Wispr Money en Kotlin puro (sin Android): se prueba en la JVM sin emulador.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Prueba la conexión real sin el emulador. Imprime conteos y nombres, nunca montos.
//   WISPR_ENDPOINT=https://<host>/mcp WISPR_TOKEN=<token> ./gradlew :wisprkit:probe
tasks.register<JavaExec>("probe") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("mx.diego.wisprkit.ProbeKt")
}
