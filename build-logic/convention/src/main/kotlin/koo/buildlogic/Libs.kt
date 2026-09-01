package koo.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.library(name: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(name).get()

fun VersionCatalog.pluginId(name: String): String =
    findPlugin(name).get().get().pluginId

fun VersionCatalog.moduleWithVersion(name: String): String =
    library(name).get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }

fun VersionCatalog.version(name: String): String =
    findVersion(name).get().requiredVersion
