plugins {
    kotlin("multiplatform")
}

kotlin {
    ohosArm64()

    sourceSets {
        commonMain.dependencies {
            api("com.tencent.kuikly-open:core:${Version.getKuiklyOhosVersion()}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

group = "com.guet.liang.kuiklytableview"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"
