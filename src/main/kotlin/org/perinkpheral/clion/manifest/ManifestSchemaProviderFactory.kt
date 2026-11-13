// SPDX-License-Identifier: MIT
package org.perinkpheral.clion.manifest

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

/** Attaches the manifest schema to `manifest.json` of an application folder. */
class ManifestSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(ManifestSchemaProvider())
}

private class ManifestSchemaProvider : JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = isApplicationManifest(file)

    override fun getName(): String = "perINKpheral application manifest"

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(javaClass, "/schemas/manifest.schema.json")

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}

/** An application folder holds `manifest.json` and at least one C source. */
fun isApplicationFolder(folder: VirtualFile?): Boolean =
    folder != null && folder.isDirectory && folder.findChild("manifest.json") != null &&
        folder.children.any { !it.isDirectory && it.extension == "c" }

fun isApplicationManifest(file: VirtualFile): Boolean =
    file.name == "manifest.json" && isApplicationFolder(file.parent)

/** The application folder that contains `file`, or null. */
fun findApplicationFolder(file: VirtualFile?): VirtualFile? {
    var folder = if (file != null && !file.isDirectory) file.parent else file
    while (folder != null) {
        if (isApplicationFolder(folder)) return folder
        folder = folder.parent
    }
    return null
}
