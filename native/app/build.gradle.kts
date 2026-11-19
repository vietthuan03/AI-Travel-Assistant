plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }
android {
    namespace = "com.example.travelassistant"
    compileSdk = 36
    defaultConfig { minSdk = 26
        applicationId = "com.example.travelassistant"
        targetSdk = 36
        versionCode = 9
        versionName = "0.5.1-gemini-recovery"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { getByName("debug") {
        val suffix = providers.gradleProperty("testApplicationIdSuffix").getOrElse(".local")
        applicationIdSuffix = suffix
        manifestPlaceholders["debugAppLabel"] = if (suffix != ".local") "@string/app_review_name" else "@string/app_name"
    } }
    buildFeatures { viewBinding = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
        implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.material)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.splash)
    implementation(project(":feature:home"))
    implementation(project(":feature:planner"))
    implementation(project(":feature:trips"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:settings"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.datastore)
    androidTestImplementation(libs.datastore)
    androidTestImplementation(libs.room.runtime)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
