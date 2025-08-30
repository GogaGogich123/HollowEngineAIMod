import net.minecraftforge.gradle.userdev.UserDevExtension
import java.time.Instant
import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm") version "1.8.22" // Compatible with MC 1.19.2 and HE Legacy
    id("net.minecraftforge.gradle") version "6.0.+"
}

version = "1.0.0"
group = "com.hollowengineai.mod"
base.archivesName.set("hollowengineai")

val minecraftVersion = "1.19.2"
val forgeVersion = "43.3.7" // Optimized version for HollowEngine Legacy
val hollowEngineVersion = "1.6.2a"

// Kotlin configuration
kotlin {
    jvmToolchain(17)
}

configure<UserDevExtension> {
    // Mappings версия для Minecraft 1.19.2
    mappings("official", minecraftVersion)
    
    runs {
        create("client") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create("hollowengineai") {
                    source(sourceSets.main.get())
                }
            }
        }
        
        create("server") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            
            // JVM optimizations for AI NPCs (up to 50)
            jvmArgs(
                "-Xmx6G",
                "-Xms3G", 
                "-XX:+UseG1GC",
                "-XX:G1HeapRegionSize=16m",
                "-XX:+UseStringDeduplication",
                "-XX:+OptimizeStringConcat",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+ParallelRefProcEnabled",
                "-Dforge.logging.console.level=info",
                "-Dhollowengine.ai.maxNpcs=50",
                "-Dhollowengine.ai.optimizeMemory=true"
            )
            
            mods {
                create("hollowengineai") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "Minecraft"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "ParchmentMC"
        url = uri("https://maven.parchmentmc.org")
    }
    maven {
        name = "HollowEngine"
        url = uri("https://maven.pkg.github.com/HollowHorizon/HollowEngine")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
    // Fallback for HollowEngine if GitHub packages not available
    flatDir {
        dirs("libs")
    }
}

dependencies {
    // Minecraft Forge
    minecraft("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")
    
    // HollowEngine Legacy - Main dependency
    // IMPORTANT: Uncomment one of the following lines based on your installation:
    
    // Option 1: If you have access to GitHub Packages
    // implementation("team.hollow.engine:hollow-engine:${hollowEngineVersion}")
    
    // Option 2: If you have the JAR file in libs/ folder  
    // implementation(files("libs/hollow-engine-${hollowEngineVersion}.jar"))
    
    // Option 3: For compilation without HollowEngine (current default)
    // Note: Runtime integration may not work without actual HollowEngine
    
    // Kotlin - Compatible versions for MC 1.19.2
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    
    // JSON обработка для LLM API
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    
    // HTTP client для Ollama - Optimized version
    implementation("com.squareup.okhttp3:okhttp:4.10.0") // Stable for MC 1.19.2
    
    // SQLite для локальной базы данных - Optimized for AI workloads
    implementation("org.xerial:sqlite-jdbc:3.42.0.0") // Stable version
    implementation("org.jetbrains.exposed:exposed-core:0.41.1") // Compatible with Kotlin 1.8.22
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    
    // Logging
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    
    // Testing - Compatible versions
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    
    // Performance monitoring for AI NPCs
    implementation("io.micrometer:micrometer-core:1.10.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xjsr305=strict", 
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xcontext-receivers" // For performance optimizations
        )
        // Optimizations for AI workloads
        allWarningsAsErrors = false
        suppressWarnings = false
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(mapOf(
            "Specification-Title" to "HollowEngineAI",
            "Specification-Vendor" to "HollowEngineAI", 
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "HollowEngineAI",
            "Implementation-Timestamp" to Instant.now(),
            "HollowEngine-Version" to hollowEngineVersion,
            "Target-MC-Version" to minecraftVersion,
            "Max-AI-NPCs" to "50"
        ))
    }
}

// Performance Profiles for different server loads
extensions.create<PerformanceProfileExtension>("performanceProfiles")

// Custom extension for performance profiles
open class PerformanceProfileExtension {
    fun lightLoad(): List<String> = listOf(
        "-Xmx3G", "-Xms2G", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50",
        "-Dhollowengine.ai.maxNpcs=10", "-Dhollowengine.ai.updateFrequency=2000"
    )
    
    fun mediumLoad(): List<String> = listOf(
        "-Xmx4G", "-Xms3G", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50", 
        "-XX:G1HeapRegionSize=16m", "-Dhollowengine.ai.maxNpcs=25",
        "-Dhollowengine.ai.updateFrequency=1500"
    )
    
    fun heavyLoad(): List<String> = listOf(
        "-Xmx6G", "-Xms4G", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=50",
        "-XX:G1HeapRegionSize=16m", "-XX:+UseStringDeduplication",
        "-XX:+OptimizeStringConcat", "-XX:+ParallelRefProcEnabled",
        "-Dhollowengine.ai.maxNpcs=50", "-Dhollowengine.ai.updateFrequency=1000",
        "-Dhollowengine.ai.optimizeMemory=true", "-Dhollowengine.ai.enableProfiling=true"
    )
}

// Task to display performance recommendations
tasks.register("performanceInfo") {
    group = "HollowEngineAI"
    description = "Display performance optimization recommendations"
    
    doLast {
        val profiles = project.extensions.getByType<PerformanceProfileExtension>()
        println("\n=== HollowEngine AI Performance Profiles ===")
        println("\nLight Load (1-10 NPCs):")
        profiles.lightLoad().forEach { println("  $it") }
        
        println("\nMedium Load (11-25 NPCs):")
        profiles.mediumLoad().forEach { println("  $it") }
        
        println("\nHeavy Load (26-50 NPCs):")
        profiles.heavyLoad().forEach { println("  $it") }
        
        println("\nTo apply profile, add JVM args to server startup script.")
        println("Example: java \${PROFILE_ARGS} -jar server.jar")
    }
}