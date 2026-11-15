package com.haira.ai.builder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ProjectZipGeneratorTest {
    @Test fun validatesAndroidPackageIds() {
        assertTrue(ProjectZipGenerator.isValidPackageId("com.example.haira"))
        assertFalse(ProjectZipGenerator.isValidPackageId("haira"))
        assertFalse(ProjectZipGenerator.isValidPackageId("com..haira"))
        assertFalse(ProjectZipGenerator.isValidPackageId("com.example-name.app"))
        assertFalse(ProjectZipGenerator.isValidPackageId("com.example.class"))
    }

    @Test fun createsAndroidStudioProjectZip() {
        val output = ByteArrayOutputStream()
        ProjectZipGenerator.write(output, "Demo App", "com.example.demo")
        val entries = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name
                zip.closeEntry()
            }
        }
        assertTrue(entries.contains("Demo App/settings.gradle.kts"))
        assertTrue(entries.contains("Demo App/app/build.gradle.kts"))
        assertTrue(entries.contains("Demo App/app/src/main/AndroidManifest.xml"))
        assertTrue(entries.contains("Demo App/app/src/main/java/com/example/demo/MainActivity.kt"))
    }
}
