plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.jeisell.alertarutas"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.jeisell.alertarutas"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Firma propia y fija. Sin esto, cada compilacion en GitHub usaria una
    // llave distinta y el telefono no dejaria actualizar la app encima de la
    // version anterior. La clave esta a la vista a proposito: es una app
    // personal. Si algun dia se publica en Google Play, hay que usar una
    // llave protegida y fuera del repositorio.
    signingConfigs {
        getByName("debug") {
            storeFile = file("alerta-rutas.jks")
            storePassword = "alertarutas"
            keyAlias = "alertarutas"
            keyPassword = "alertarutas"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Sin librerias externas a proposito: solo el SDK de Android y Kotlin.
}
