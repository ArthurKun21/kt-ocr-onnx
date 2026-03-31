import com.diffplug.gradle.spotless.SpotlessExtension
import koo.buildlogic.libs
import koo.buildlogic.pluginId
import koo.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("unused")
class CodeLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.pluginId("spotless"))
            }

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt", "**/*.kts")
                    targetExclude("**/build/**/*.kt")
                    ktlint(libs.version("ktlint-core")).editorConfigOverride(
                        mapOf("ktlint_standard_annotation" to "disabled"),
                    )
                    trimTrailingWhitespace()
                    endWithNewline()
                }
                format("xml") {
                    target("**/*.xml")
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
    }
}
