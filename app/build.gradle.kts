import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystoreProperties = Properties()
rootProject.file("keystore.properties").inputStream().use {
    keystoreProperties.load(it)
}

android {
    namespace = "com.YSNB.yuanshen"
    compileSdk = 36
    enableKotlin = false

    defaultConfig {
        applicationId = "com.YSNB.yuanshen"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)

}
