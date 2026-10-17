plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

javafx {
    version = "27"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("io.github.mkpaz:atlantafx-base:3.0.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.example.budget.Main"
    // sqlite-jdbc (unnamed module) and JavaFX load native libraries; allow it without JDK warnings.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED,javafx.graphics")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    // `./gradlew run -Dbudget.dataDir=...` keeps dev runs away from the real %APPDATA% data;
    // `-Dbudget.cnbUrl=...` points the ČNB Rate source elsewhere.
    // The UI language is chosen on first launch and in Settings.
    listOf("budget.dataDir", "budget.cnbUrl").forEach { name ->
        System.getProperty(name)?.let { systemProperty(name, it) }
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
