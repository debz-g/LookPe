import java.security.SecureRandom

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/*
 * The AES-256 key for .lookpe backups lives in backup.key (64 hex chars) in the project root.
 * It's git-ignored: the repo is public, and anyone holding the key can read backups made with it.
 * Missing on a fresh clone? A random one is created, so your builds can only open your own backups.
 * Keep the file safe: losing it means backups made by your builds can't be restored.
 */
val backupKeyHex: String = rootProject.file("backup.key").let { file ->
    if (!file.exists()) {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.writeText(bytes.joinToString("") { "%02x".format(it) } + "\n")
        logger.warn("LookPe: created a new random backup key in ${file.path}. Keep it safe.")
    }
    file.readText().trim().also {
        require(Regex("[0-9a-fA-F]{64}").matches(it)) { "backup.key must be 64 hex characters (32 bytes)" }
    }
}
// Compiled in as two random-masked halves so the whole key never appears as one string in the APK.
val backupKeyMask = ByteArray(32).also { SecureRandom().nextBytes(it) }
val backupKeyMasked = backupKeyHex.chunked(2).mapIndexed { i, h -> (h.toInt(16) xor (backupKeyMask[i].toInt() and 0xff)).toByte() }
fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
fun List<Byte>.hex() = joinToString("") { "%02x".format(it) }

android {
    namespace = "dev.redfox.lookpe"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.redfox.lookpe"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.2"

        buildConfigField("String", "BACKUP_KEY_A", "\"${backupKeyMask.hex()}\"")
        buildConfigField("String", "BACKUP_KEY_B", "\"${backupKeyMasked.hex()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Sideloaded onto one phone, so the debug key is fine.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.obsidian)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.robolectric)
}

// Keeps screens on Obsidian: fails `check` when code reaches past the design system.
val verifyDesignRules = tasks.register("verifyDesignRules") {
    group = "verification"
    val sources = fileTree("src/main/java") { include("**/*.kt"); exclude("**/widget/**") }
    inputs.files(sources)
    doLast {
        val rules = listOf(
            Regex("""\bMaterialTheme\.""") to "use ObsidianTheme.colors / typography",
            Regex("""\.(combinedC|c)lickable\s*[({]""") to "use Modifier.pressable { }",
            Regex("""\bripple\s*\(""") to "no ripples; use Modifier.pressable",
            Regex("""\bColor\s*\(\s*0x""") to "hard-coded colour; use a token",
        )
        val problems = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().flatMapIndexed { i, line ->
                val code = line.substringBefore("//")
                rules.filter { (pattern, _) -> pattern.containsMatchIn(code) }
                    .map { (_, why) -> "${file.path}:${i + 1}: $why" }
            }
        }
        if (problems.isNotEmpty()) throw GradleException("Obsidian design rules:\n" + problems.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(verifyDesignRules) }
