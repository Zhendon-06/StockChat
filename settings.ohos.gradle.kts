import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.attributes.Attribute

// SQLDelight 2.1.0 publishes the runtime's native implementation as a
// linux-arm64 klib. OpenHarmony uses the same Kotlin/Native ABI here, but has
// its own target name, so make that published variant selectable for OHOS.
abstract class SqlDelightOhosRuntimeRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.addVariant(
            "ohosArm64ApiElements-published",
            "linuxArm64ApiElements-published",
        ) {
            attributes {
                attribute(
                    Attribute.of("org.jetbrains.kotlin.native.target", String::class.java),
                    "ohos_arm64",
                )
            }
        }
    }
}

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }

    components {
        withModule(
            "app.cash.sqldelight:runtime",
            SqlDelightOhosRuntimeRule::class.java,
        )
        withModule(
            "app.cash.sqldelight:runtime-linuxarm64",
            SqlDelightOhosRuntimeRule::class.java,
        )
    }
}

rootProject.name = "StockChat"

val buildFileName = "build.ohos.gradle.kts"
rootProject.buildFileName = buildFileName

include(":androidApp")
include(":shared")
include(":table-core")
project(":shared").buildFileName = buildFileName
project(":table-core").buildFileName = buildFileName

include(":kuikly-chart")
project(":kuikly-chart").buildFileName = buildFileName
