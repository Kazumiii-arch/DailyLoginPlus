plugins {
    java
    // Plugin for creating a fat JAR (including all dependencies)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.vortex" // Changed group to match your company name
version = "1.18.0" // Updated version to 1.18.0 for DailyLogin+

repositories {
    mavenCentral()
    // SpigotMC snapshots repository for Spigot API
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    // JitPack for various libraries (e.g., DecentHolograms, if you re-add it later)
    maven { url = uri("https://jitpack.io/") }
    // PlaceholderAPI repository
    maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
    // Vault repository
    maven { url = uri("https://repo.essentialsx.net/releases/") } // Vault is often found here or JitPack
}

dependencies {
    // Spigot API for plugin development
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT") // Compatible with 1.17+
    
    // PlaceholderAPI (soft dependency)
    compileOnly("me.clip:placeholderapi:2.11.5")
    
    // Vault for economy and permissions integration (soft dependency)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") // Use VaultAPI for compileOnly
    // At runtime, Vault itself will be present on the server
    
    // Gson for JSON serialization/deserialization (for PlayerData maps in MySQL)
    implementation("com.google.code.gson:gson:2.10.1") // Use implementation as it's a direct dependency
    
    // HikariCP for robust MySQL connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0") // Use implementation as it's a direct dependency
    
    // MySQL Connector/J for database connectivity (HikariCP requires this)
    implementation("mysql:mysql-connector-java:8.0.33") // Use a compatible version for your MySQL server
    
    // If you plan to re-add DecentHolograms later, uncomment this:
    // compileOnly("com.github.decentsoftware-eu:decentholograms:2.9.3")
}

java {
    // Specifies the Java version to use for compilation
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    shadowJar {
        // Sets the base name of the shaded JAR file
        archiveBaseName.set("DailyLoginPlus") // Changed from "GachaCrate" to "DailyLoginPlus"
        // No classifier (e.g., "-all", "-dev")
        archiveClassifier.set("")
        // Sets the version of the shaded JAR file to the project's version
        archiveVersion.set(project.version.toString())
    }

    build {
        // Ensures shadowJar task runs as part of the build process
        dependsOn(shadowJar)
    }
    
    // Sets UTF-8 encoding for Java compilation
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
