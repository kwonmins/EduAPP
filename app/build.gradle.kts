plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.myhealth"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myhealth"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildFeatures { compose = true }

    // ✅ Java 17로 통일
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    // ✅ Kotlin도 17 (툴체인/로컬 JDK 어느 쪽이든 OK)
    kotlinOptions { jvmTarget = "17" }
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // Compose/BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.compose.ui:ui-text")
    // Network / Data
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ✅ MySQL JDBC 커넥터 — 하나만 사용
    implementation("mysql:mysql-connector-java:5.1.49")

    // (만약 DEX/클래스 버전 관련 에러가 난다면 아래로 교체)
    // implementation("mysql:mysql-connector-java:8.0.33")
    implementation("androidx.compose.material:material-icons-extended")
    // ✅ Java API desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

android {
    // META-INF 충돌 방지 (JDBC 포함 시 흔함)

    packaging {

        resources {

            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST"
            )
        }
    }
}
