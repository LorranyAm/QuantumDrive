plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Número da versão vem do GitHub Actions (cada compilação aumenta 1)
val numeroBuild = (project.findProperty("numeroBuild") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.quantumdrive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quantumdrive.app"
        minSdk = 26
        targetSdk = 35
        versionCode = numeroBuild
        versionName = "2.0.$numeroBuild"
    }

    // Assinatura: a senha fica no segredo QD_KEYSTORE_PASSWORD do GitHub.
    // Sempre a mesma chave → o celular aceita as atualizações por cima do app instalado.
    signingConfigs {
        create("release") {
            val arquivo = System.getenv("QD_KEYSTORE_FILE")
            if (arquivo != null) {
                storeFile = file(arquivo)
                storePassword = System.getenv("QD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QD_KEY_ALIAS")
                keyPassword = System.getenv("QD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("QD_KEYSTORE_FILE") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
