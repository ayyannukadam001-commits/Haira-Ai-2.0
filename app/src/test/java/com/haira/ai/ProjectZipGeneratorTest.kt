package com.haira.ai

import com.haira.ai.builder.ProjectZipGenerator
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectZipGeneratorTest {
    @Test
    fun exportsAndroidStudioProjectWithManifestAndComposeActivity() {
        val output = ByteArrayOutputStream()
        ProjectZipGenerator.write(output, "Demo App", "com.example.demo")

        val files = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name.substringAfter('/')] =
                    zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }

        assertTrue(files.containsKey("settings.gradle.kts"))
        assertTrue(files.containsKey("build.gradle.kts"))
        assertTrue(files.containsKey("app/build.gradle.kts"))
        assertTrue(files.containsKey("app/src/main/AndroidManifest.xml"))
        assertTrue(files.containsKey("app/src/main/java/com/example/demo/MainActivity.kt"))
        assertTrue(files["app/src/main/java/com/example/demo/MainActivity.kt"]!!.contains("package com.example.demo"))
        assertTrue(files["app/src/main/AndroidManifest.xml"]!!.contains("android.intent.action.MAIN"))
    }

    @Test
    fun rejectsInvalidPackageIds() {
        assertTrue(ProjectZipGenerator.isValidPackageId("com.example.valid_app"))
        assertEquals(false, ProjectZipGenerator.isValidPackageId("com..invalid"))
        assertEquals(false, ProjectZipGenerator.isValidPackageId("com.example.class"))
    }
}
