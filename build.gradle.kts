@file:Suppress("UnstableApiUsage")

plugins {
    id("quiet-fabric-loom") version ("1.10-SNAPSHOT")
    kotlin("jvm") version ("2.2.0")
}

val modId = project.properties["mod_id"].toString()
version = project.properties["version"].toString()
group = project.properties["group"].toString()

val minecraftVersion = project.properties["minecraft_version"].toString()

base.archivesBaseName = project.properties["mod_archive_name"].toString()

repositories {
    // Local mirror: the Cobblemon artifact is ~130 MB and the upstream maven drops the connection
    // part way through, so it is vendored into tools/local-maven instead.
    maven {
        name = "LocalMirror"
        url = uri("../tools/local-maven")
        content {
            includeGroup("com.cobblemon")
        }
    }
    // Regional mirrors first; the upstream hosts below are reachable but rate limited to a few KB/s.
    maven("https://bmclapi2.bangbang93.com/maven") { name = "BMCLAPI" }
    maven("https://maven.aliyun.com/repository/public") { name = "AliyunPublic" }
    maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") { name = "TencentPublic" }
    mavenCentral()
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://maven.parchmentmc.org")
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots1"
        mavenContent { snapshotsOnly() }
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://repo.lucko.me")
    maven("https://maven.pokeskies.com/releases/")
    // Optional client-side integrations: Cloth Config (settings widgets) and Mod Menu (gear button).
    // Both are compile-only; users install them separately.
    maven("https://maven.shedaniel.me/") { name = "Shedaniel" }
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

val modImplementationInclude by configurations.register("modImplementationInclude")

configurations {
    modImplementationInclude
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.layered {
        officialMojangMappings()
        // TODO: Fix hardcoded minecraft version once Parchment updates
        parchment("org.parchmentmc.data:parchment-1.21:${project.properties["parchment_version"]}")
    })
    modImplementation("net.fabricmc:fabric-loader:${project.properties["loader_version"].toString()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.properties["fabric_kotlin_version"].toString()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.properties["fabric_version"].toString()}")

    // Adventure Text!
    modImplementation(include("net.kyori:adventure-platform-fabric:5.14.2")!!)

    // PermissionsAPI
    modImplementation("me.lucko:fabric-permissions-api:0.3.1")?.let {
        include(it)
    }

    modImplementation("com.cobblemon:fabric:1.7.0+1.21.1")

    modImplementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Optional client-side integrations. Compile-only: they are never bundled, and the client
    // code that touches them is guarded by isModLoaded checks at runtime.
    add("modClientCompileOnly", "me.shedaniel.cloth:cloth-config-fabric:${project.properties["cloth_config_version"]}")
    add("modClientCompileOnly", "com.terraformersmc:modmenu:${project.properties["modmenu_version"]}")
}

tasks.processResources {
    inputs.property("version", version)

    filesMatching("fabric.mod.json") {
        expand("id" to modId, "version" to version)
    }

    filesMatching("**/lang/*.json") {
        expand("id" to modId)
    }
}

tasks.remapJar {
    val archiveBase = project.properties["mod_archive_name"].toString()
    archiveFileName.set("$archiveBase-fabric-$minecraftVersion-${project.version}.jar")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.withType<AbstractArchiveTask> {
    from("LICENSE") {
        rename { "${it}_${modId}" }
    }
    // Apache-2.0 section 4(d): a redistributor must include any NOTICE file. Upstream ships none,
    // so this fork ships ATTRIBUTION.md alongside the licence text instead.
    from("ATTRIBUTION.md")
}
