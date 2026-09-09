import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("app.cash.sqldelight")
    id("maven-publish")

}

val KEY_PAGE_NAME = "pageName"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            freeCompilerArgs = freeCompilerArgs + getCommonCompilerArgs()
            isStatic = true
            license = "MIT"
        }
    }

    ohosArm64 {
        binaries.sharedLib {
            // The shared Kotlin/Native linker configuration inherited by this
            // project asks for sqlite3. OHOS exposes libc and sqlite through
            // its system image instead of a development-time libsqlite3.so;
            // the demo does not instantiate the native SQL driver on OHOS.
            linkerOpts("-L${rootProject.file(".ohos-build").absolutePath}")
        }
    }

    sourceSets {
        val commonMain by getting {
            // DevEco invokes the OHOS target through an incremental Gradle
            // task graph. SQLDelight's KMP plugin does not always attach its
            // generated common sources to that graph, so register the output
            // directory explicitly as part of commonMain.
            kotlin.srcDir(layout.buildDirectory.dir("generated/sqldelight/code/StockChatDatabase/commonMain"))
            dependencies {
                implementation(project(":table-core"))
                implementation(project(":kuikly-chart"))
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuikly-open:core-annotations:${Version.getKuiklyOhosVersion()}")
                implementation("com.tencent.kuiklybase:KuiklyMarkdown:1.0.6-2.0.21-ohos")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:2.0.21-coroutines-KBA-001")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1-KBA-003")
                implementation("app.cash.sqldelight:runtime:2.1.0")

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyOhosVersion()}")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

sqldelight {
    databases {
        create("StockChatDatabase") {
            packageName.set("com.guet.liang.stockchat.database")
        }
    }
}

// SQLDelight's linux-arm64 runtime is ABI-compatible with the OHOS target,
// but the klib records linux_arm64 in both its manifest and target directory.
// Rewrite the downloaded artifact once before Kotlin/Native consumes it.
val patchOhosSqlDelightRuntime = tasks.register("patchOhosSqlDelightRuntime") {
    doLast {
        configurations.findByName("ohosArm64CompileKlibraries")
            ?.resolve()
            ?.filter { it.name == "runtime.klib" && it.path.contains("runtime-linuxarm64") }
            ?.forEach { klib ->
                val temporary = klib.resolveSibling("${klib.name}.ohos")
                ZipFile(klib).use { input ->
                    ZipOutputStream(temporary.outputStream()).use { output ->
                        input.entries().asSequence().forEach { entry ->
                            val rewrittenName = entry.name.replace("linux_arm64", "ohos_arm64")
                            var bytes = input.getInputStream(entry).use { it.readBytes() }
                            if (rewrittenName == "default/manifest") {
                                bytes = bytes
                                    .toString(Charsets.UTF_8)
                                    .replace("native_targets=linux_arm64", "native_targets=ohos_arm64")
                                    .toByteArray(Charsets.UTF_8)
                            }
                            output.putNextEntry(ZipEntry(rewrittenName))
                            output.write(bytes)
                            output.closeEntry()
                        }
                    }
                }
                temporary.copyTo(klib, overwrite = true)
                temporary.delete()
            }
    }
}

tasks.matching {
    it.name == "kspKotlinOhosArm64" || it.name == "compileKotlinOhosArm64"
}.configureEach {
    dependsOn(patchOhosSqlDelightRuntime)
    dependsOn("generateCommonMainStockChatDatabaseInterface")
}

group = "com.guet.liang.stockchat"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"

publishing {
    repositories {
        maven {
            credentials {
                username = System.getenv("mavenUserName") ?: ""
                password = System.getenv("mavenPassword") ?: ""
            }
            rootProject.properties["mavenUr?"]?.toString()?.let { url = uri(it) }
        }
    }
}

ksp {
    arg(KEY_PAGE_NAME, getPageName())
}

dependencies {
    compileOnly("com.tencent.kuikly-open:core-ksp:${Version.getKuiklyOhosVersion()}") {
        add("kspAndroid", this)
        add("kspIosArm64", this)
        add("kspIosX64", this)
        add("kspIosSimulatorArm64", this)
        add("kspOhosArm64", this)
    }
}

android {
    namespace = "com.guet.liang.stockchat.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }
    sourceSets {
        named("main") {
            assets.srcDirs("src/commonMain/assets")
        }
    }
}

fun getPageName(): String {
    return (project.properties[KEY_PAGE_NAME] as? String) ?: ""
}

fun getCommonCompilerArgs(): List<String> {
    return listOf(
        "-Xallocator=std"
    )
}

fun getLinkerArgs(): List<String> {
    return listOf()
}
