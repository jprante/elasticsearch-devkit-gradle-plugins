package org.xbib.gradle.plugin.elasticsearch.test

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Copy
import org.xbib.gradle.plugin.elasticsearch.plugin.PluginBuildPlugin
import org.xbib.gradle.task.elasticsearch.test.ClusterFormationTasks

/**
 * A plugin to run messy tests, which are generally tests that depend on plugins.
 *
 * This plugin will add the same test configuration as standalone tests, except
 * also add the plugin-metadata and properties files for each plugin project
 * dependency.
 */
class MessyTestPlugin extends StandaloneTestPlugin {

    @Override
    void apply(Project project) {
        super.apply(project)

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

        // add each generated dir to the test classpath in IDEs
        project.idea.module.singleEntryLibraries= ['TEST': [project.file(outputDir)]]
        // Eclipse doesn't need this because it gets the entire module as a dependency
    }
}
