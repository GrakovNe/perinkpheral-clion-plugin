// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.project

import org.perinkpheral.clion.build.AppBuilder
import org.perinkpheral.clion.build.Sdk
import org.perinkpheral.clion.settings.PerinkpheralSettings
import java.io.File

/**
 * `~/.perinkpheral/app.cmake`: how CMake reads and compiles an application, from the
 * plugin's settings. An application's CMakeLists.txt written by the plugin includes it
 * before project(). CMake compiles with clang of wasi-sdk and the flags of the plugin's
 * build; the package, its checks and running it are the plugin's.
 *
 * strings.h and pictures.h are made by the plugin into
 * `~/.perinkpheral/generated/<name>`, where the code finds them.
 */
object ToolchainFile {
    const val NAME = "app.cmake"

    val file: File get() = File(PerinkpheralSettings.managedDirectory(), NAME)

    /** Where the headers made of an application's strings and pictures go. */
    fun generated(name: String): File = File(File(PerinkpheralSettings.managedDirectory(), "generated"), name)

    /** CMake's own spelling of a path: forward slashes on every system. */
    private fun cmakePath(path: String): String = path.replace('\\', '/').replace("\"", "\\\"")

    /** Writes the file from the current settings. Returns true when its content changed. */
    fun write(): Boolean {
        val settings = PerinkpheralSettings.get()
        val sdk = cmakePath(settings.sdkPath)
        val wasi = cmakePath(settings.wasiSdkPath)
        val generated = cmakePath(File(PerinkpheralSettings.managedDirectory(), "generated").path)
        val page = AppBuilder.WASM_PAGE
        val stack = AppBuilder.APP_STACK
        val text = """
            |# Written by the perINKpheral plugin for CLion from its settings.
            |# Rewritten whenever they change: edit them in Settings | Tools | perINKpheral.
            |include_guard(GLOBAL)
            |set(PERINKPHERAL "$sdk")
            |set(ENV{WASI_SDK_PATH} "$wasi")
            |if(NOT CMAKE_TOOLCHAIN_FILE)
            |    set(CMAKE_TOOLCHAIN_FILE "${'$'}{PERINKPHERAL}/sdk/cmake/wasm32-toolchain.cmake")
            |endif()
            |
            |# An application: its .c files with the SDK's library, as the plugin's build compiles them.
            |function(perinkpheral_app app_dir)
            |    get_filename_component(app_dir "${'$'}{app_dir}" ABSOLUTE)
            |    get_filename_component(name "${'$'}{app_dir}" NAME)
            |    set(generated "$generated/${'$'}{name}")
            |    file(GLOB sources CONFIGURE_DEPENDS "${'$'}{app_dir}/*.c" "${'$'}{app_dir}/*.h")
            |    file(GLOB library CONFIGURE_DEPENDS "${'$'}{PERINKPHERAL}/sdk/lib/*.c")
            |    file(GLOB made CONFIGURE_DEPENDS "${'$'}{generated}/*.c")
            |    add_executable(${'$'}{name} ${'$'}{sources} ${'$'}{library} ${'$'}{made})
            |    set_target_properties(${'$'}{name} PROPERTIES OUTPUT_NAME app SUFFIX ".wasm" C_STANDARD 11)
            |    target_include_directories(${'$'}{name} PRIVATE "${'$'}{PERINKPHERAL}/sdk" "${'$'}{PERINKPHERAL}/sdk/lib"
            |                               "${'$'}{generated}" "${'$'}{app_dir}")
            |    target_compile_options(${'$'}{name} PRIVATE -nostdlib -ffreestanding -fno-builtin -Oz -flto
            |                           -Wall -Wextra -Werror)
            |    target_link_options(${'$'}{name} PRIVATE -nostdlib -flto -Wl,--no-entry -Wl,--strip-all
            |                        -Wl,--gc-sections -Wl,--initial-memory=$page -Wl,--max-memory=$page
            |                        -Wl,-z,stack-size=$stack)
            |endfunction()
            |""".trimMargin()
        val target = file
        if (target.isFile && target.readText() == text) {
            return false
        }
        target.parentFile.mkdirs()
        target.writeText(text)
        return true
    }

    /** Makes strings.h, strings.c and pictures.h of an application for the code to read.
     * Returns null, or what is wrong with its strings or pictures. */
    fun writeHeaders(appDir: File): String? {
        val settings = PerinkpheralSettings.get()
        if (!settings.isSdkValid) return null
        return try {
            val into = generated(appDir.name)
            into.deleteRecursively()
            AppBuilder(Sdk(File(settings.sdkPath)), File(settings.wasiSdkPath)) {}.headers(appDir, into)
            null
        } catch (error: Exception) {
            error.message
        }
    }
}
