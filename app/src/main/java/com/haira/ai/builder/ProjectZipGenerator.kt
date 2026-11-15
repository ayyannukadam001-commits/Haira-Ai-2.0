package com.haira.ai.builder

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectZipGenerator {
    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    private val reservedSegments = setOf("class", "interface", "object", "when", "is", "in", "fun", "val", "var", "package", "import", "return", "this", "super", "if", "else", "for", "while", "do", "try", "catch", "finally", "throw", "true", "false", "null")

    fun isValidPackageId(value: String): Boolean = packagePattern.matches(value) && value.split('.').none { it in reservedSegments }

    fun write(output: OutputStream, appName: String, packageId: String) {
        require(appName.isNotBlank()) { "Enter an app name." }
        require(isValidPackageId(packageId)) { "Package ID must look like com.example.myapp." }
        val safeName = appName.trim().filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }.take(40).ifBlank { "AndroidProject" }
        val packagePath = packageId.replace('.', '/')
        val escapedName = safeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val kotlinName = safeName.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val files = linkedMapOf(
            "settings.gradle.kts" to """
                pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = "$safeName"
                include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    id("com.android.application") version "8.7.3" apply false
                    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
                    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
                }
            """.trimIndent(),
            "gradle.properties" to """
                org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
                android.useAndroidX=true
                kotlin.code.style=official
            """.trimIndent(),
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                    id("org.jetbrains.kotlin.plugin.compose")
                }
                android {
                    namespace = "$packageId"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "$packageId"
                        minSdk = 26
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0"
                    }
                    buildFeatures { compose = true }
                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlinOptions { jvmTarget = "17" }
                }
                dependencies {
                    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
                    implementation("androidx.activity:activity-compose:1.10.0")
                    implementation("androidx.compose.ui:ui")
                    implementation("androidx.compose.ui:ui-tooling-preview")
                    implementation("androidx.compose.material3:material3")
                    debugImplementation("androidx.compose.ui:ui-tooling")
                }
            """.trimIndent(),
            "app/src/main/AndroidManifest.xml" to """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:theme="@android:style/Theme.Material.Light.NoActionBar"
                        android:label="$escapedName" android:allowBackup="false">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/src/main/java/$packagePath/MainActivity.kt" to """
                package $packageId

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.foundation.layout.Arrangement
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Surface
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Alignment
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.sp

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { WelcomeScreen() } } }
                    }
                }

                @Composable
                private fun WelcomeScreen() {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("$kotlinName", style = MaterialTheme.typography.headlineMedium)
                        Text("Your Android project is ready to customize.", fontSize = 14.sp)
                    }
                }
            """.trimIndent(),
            "README.md" to """
                # $safeName

                Starter Android app generated by HAIRA AI Builder.

                ## Open
                1. Extract this ZIP.
                2. Open the extracted folder in Android Studio.
                3. Allow Gradle sync to finish.
                4. Run the app on an emulator or connected Android device.

                ## Project details
                - Package: $packageId
                - Kotlin + Jetpack Compose
                - Minimum Android API: 26
                - Compile / target API: 35

                This is a source-project starter, not a compiled APK. Build the APK from Android Studio.
            """.trimIndent()
        )

        ZipOutputStream(output.buffered()).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(safeName + "/" + path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
