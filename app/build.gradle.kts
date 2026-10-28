plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // KSP = Kotlin Symbol Processing. It reads Room's @Entity/@Dao annotations at
    // compile time and generates the SQL code, like Lombok or MapStruct in Java.
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.krishnagurram.hometodo"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.krishnagurram.hometodo"
        minSdk = 29          // Android 10+. Your Fold is far above this.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    // Room writes a JSON snapshot of the schema per version. When you change a
    // table later, diffing these files shows exactly what migrated.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // The BOM pins every Compose library to versions known to work together,
    // so the Compose lines below carry no version of their own.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
