// SPDX-License-Identifier: MIT
package dev.perinkpheral.clion.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class GeneratedFilesTest {
    @Test
    fun separateCheckoutsCannotOverwriteEachOthersHeaders() {
        val temporary = Files.createTempDirectory("perinkpheral-headers").toFile()
        try {
            val first = File(temporary, "main/clock").apply { mkdirs() }
            val second = File(temporary, "feature/clock").apply { mkdirs() }
            val root = File(temporary, "generated")
            val firstOutput = GeneratedFiles.directory(root, first).apply { mkdirs() }
            val secondOutput = GeneratedFiles.directory(root, second).apply { mkdirs() }
            File(firstOutput, "strings.h").writeText("first")
            File(secondOutput, "strings.h").writeText("second")
            assertNotEquals(firstOutput, secondOutput)
            assertEquals("first", File(firstOutput, "strings.h").readText())
            assertEquals("second", File(secondOutput, "strings.h").readText())
            assertEquals(64, firstOutput.parentFile.name.length)
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun canonicalAliasesShareTheSameDirectory() {
        val temporary = Files.createTempDirectory("perinkpheral-alias").toFile()
        try {
            val app = File(temporary, "source/clock").apply { mkdirs() }
            val root = File(temporary, "generated")
            val expected = GeneratedFiles.directory(root, app)
            assertEquals(expected, GeneratedFiles.directory(root, File(app, ".")))
            assertEquals(expected, GeneratedFiles.directory(root, File(app, "../clock")))
            val alias = File(temporary, "alias")
            Files.createSymbolicLink(alias.toPath(), app.parentFile.toPath())
            assertEquals(expected, GeneratedFiles.directory(root, File(alias, "clock")))
            alias.delete()
        } finally {
            temporary.deleteRecursively()
        }
    }

    @Test
    fun unsupportedSdkHasAnExplicitUpdateInstruction() {
        val temporary = Files.createTempDirectory("perinkpheral-sdk").toFile()
        try {
            val module = File(temporary, "sdk/cmake/perinkpheral.cmake")
            module.parentFile.mkdirs()
            module.writeText("set(generated old)")
            val problem = assertFailsWith<IOException> { GeneratedFiles.requireSdkSupport(temporary) }
            assertEquals(GeneratedFiles.SDK_UPDATE, problem.message)
            module.writeText("if(PERINKPHERAL_GENERATED_BY_PATH)\nendif()")
            GeneratedFiles.requireSdkSupport(temporary)
        } finally {
            temporary.deleteRecursively()
        }
    }
}
