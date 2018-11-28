package org.xbib.gradle.plugin.forbiddenapis

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin

/**
 * Forbidden Apis Gradle Plugin.
 */
class ForbiddenApisPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(JavaBasePlugin)
        def forbiddenTask = project.tasks.create(ForbiddenApisTask.FORBIDDENAPIS_TASK_NAME, ForbiddenApisTask) { task ->
            description = "Runs forbidden-apis checks"
            group = JavaBasePlugin.VERIFICATION_GROUP
        }
        project.sourceSets.all{ sourceSet ->
            def getSourceSetClassesDirs = {
                sourceSet.output.hasProperty('classesDirs') ?
                    sourceSet.output.classesDirs : project.files(sourceSet.output.classesDir)
            }
            String taskName = sourceSet.getTaskName(ForbiddenApisTask.FORBIDDENAPIS_TASK_NAME, null)
            project.tasks.create(taskName, ForbiddenApisTask) { task ->
                description = "Runs forbidden-apis checks on '${sourceSet.name}' classes"
                task.conventionMapping.with {
                    classesDirs = { getSourceSetClassesDirs() }
                    classpath = { sourceSet.compileClasspath }
                    targetCompatibility = { project.targetCompatibility.toString() }
                    signaturesFiles = project.files()
                    disableClassloadingCache = true
                }
                project.afterEvaluate{
                    def sourceSetDirs = getSourceSetClassesDirs().files;
                    if (classesDirs.any { sourceSetDirs.contains(it) } ) {
                        task.dependsOn(sourceSet.output)
                    }
                }
                forbiddenTask.dependsOn(task)
            }
        }
    }
}
