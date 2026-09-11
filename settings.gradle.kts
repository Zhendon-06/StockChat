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
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

rootProject.name = "StockChat"
include(":androidApp")
include(":shared")
include(":table-core")
// These optional Kuikly hosts are supplied only in distributions that include
// their source directories. Avoid configuring missing project directories so
// Gradle 9 does not reject the build during settings evaluation.
if (file("h5App").isDirectory) {
    include(":h5App")
}
if (file("miniApp").isDirectory) {
    include(":miniApp")
}

include(":kuikly-chart")
