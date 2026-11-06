plugins {
    java
}

group = "dev.mattron1c"
version = providers.gradleProperty("version").get()

val paperVersion = providers.gradleProperty("paperVersion").get()
val javaRelease = providers.gradleProperty("javaRelease").getOrElse("21").toInt()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:${providers.gradleProperty("faweVersion").get()}")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:${providers.gradleProperty("faweVersion").get()}") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaRelease)
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-Xlint:-deprecation", "-Xlint:-removal"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveFileName.set("FastAsyncWorldGuard-${project.version}.jar")
    from("LICENSE") { into("META-INF") }
    manifest {
        attributes(
            "Implementation-Title" to "FastAsyncWorldGuard",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "mattron1c"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
