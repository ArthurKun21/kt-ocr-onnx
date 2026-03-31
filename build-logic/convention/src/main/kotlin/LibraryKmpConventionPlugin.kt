import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class LibraryKmpConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("koo.kmp.base")
            }
        }
    }
}
