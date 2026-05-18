import koo.buildlogic.configureMavenPublish
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class MavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish")

            configureMavenPublish()
        }
    }
}