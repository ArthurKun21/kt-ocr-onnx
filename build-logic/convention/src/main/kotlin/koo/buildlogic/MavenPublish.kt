package koo.buildlogic

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.getByType

private const val GitHubActorEnv = "GITHUB_ACTOR"
private const val GitHubTokenEnv = "GITHUB_TOKEN"
const val MAVEN_PUBLISH_GROUP_ID = "com.github.ArthurKun21"

internal fun Project.configureMavenPublish() {
    extensions.configure<MavenPublishBaseExtension> {
        pom {
            url.set("https://github.com/ArthurKun21/kt-ocr-onnx")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("ArthurKun21")
                    name.set("Arthur")
                    email.set("16458204+ArthurKun21@users.noreply.github.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/ArthurKun21/kt-ocr-onnx.git")
                developerConnection.set("scm:git:ssh://github.com/ArthurKun21/kt-ocr-onnx.git")
                url.set("https://github.com/ArthurKun21/kt-ocr-onnx")
            }
        }
    }

    extensions.getByType<PublishingExtension>().repositories.maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/ArthurKun21/kt-ocr-onnx")
        credentials(PasswordCredentials::class) {
            username = providers.environmentVariable(GitHubActorEnv).orNull
            password = providers.environmentVariable(GitHubTokenEnv).orNull
        }
    }
}
