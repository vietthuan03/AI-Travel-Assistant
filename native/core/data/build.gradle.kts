plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.ksp); alias(libs.plugins.hilt) }

fun bookingUrlLiteral(property: String): String {
    val url = providers.gradleProperty(property).orNull.orEmpty().trim()
    require(url.none { it.isISOControl() }) { "Invalid booking URL property $property" }
    return "\"${url.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.example.travelassistant.core.data"
    compileSdk = 36
    defaultConfig { minSdk = 26
        
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    defaultConfig {
        buildConfigField("String", "BACKEND_URL", "\"\"")
        buildConfigField("String", "FLIGHT_SEARCH_URL", bookingUrlLiteral("flightSearchUrlTemplate"))
        buildConfigField("String", "STAY_SEARCH_URL", bookingUrlLiteral("staySearchUrlTemplate"))
    }
    buildTypes { getByName("debug") { buildConfigField("String", "BACKEND_URL", "\"http://127.0.0.1:8787\"") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
        implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core:domain"))
    implementation(libs.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
