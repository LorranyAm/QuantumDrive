plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Número da versão vem do GitHub Actions (cada compilação aumenta 1)
val numeroBuild = (project.findProperty("numeroBuild") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.quantumdrive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quantumdrive.app"
        minSdk = 24
        targetSdk = 35
        versionCode = numeroBuild
        versionName = "1.0.$numeroBuild"
    }

    // Assinatura: a chave fica num segredo do GitHub (QD_KEYSTORE_BASE64 etc.).
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
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
