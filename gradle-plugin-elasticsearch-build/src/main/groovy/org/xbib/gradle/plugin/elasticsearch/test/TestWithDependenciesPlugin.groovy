package org.xbib.gradle.plugin.elasticsearch.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Copy
import org.xbib.gradle.plugin.elasticsearch.plugin.PluginBuildPlugin
import org.xbib.gradle.task.elasticsearch.test.ClusterFormationTasks

/**
 * A plugin to run tests that depend on other plugins or modules.
 *
 * This plugin will add the plugin-metadata and properties files for each
 * dependency to the test source set.
 */
class TestWithDependenciesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (project.isEclipse) {
            /* The changes this plugin makes both break and aren't needed by
             * Eclipse. This is because Eclipse flattens main and test
             * dependencies into a single dependency. Because Eclipse is
             * "special".... */
            return
        }

        project.configurations.testCompile.dependencies.all { Dependency dep ->
            // this closure is run every time a compile dependency is added
            if (dep instanceof ProjectDependency && dep.dependencyProject.plugins.hasPlugin(PluginBuildPlugin)) {
                project.gradle.projectsEvaluated {
                    addPluginResources(project, dep.dependencyProject)
                }
            }
        }
    }

    private static addPluginResources(Project project, Project pluginProject) {
        String outputDir = "${project.buildDir}/generated-resources/${pluginProject.name}"
        String taskName = ClusterFormationTasks.pluginTaskName("copy", pluginProject.name, "Metadata")
        Copy copyPluginMetadata = project.tasks.create(taskName, Copy.class)
        copyPluginMetadata.into(outputDir)
        copyPluginMetadata.from(pluginProject.tasks.pluginProperties)
        copyPluginMetadata.from(pluginProject.file('src/main/plugin-metadata'))
        project.sourceSets.test.output.dir(outputDir, builtBy: taskName)
    }
}
