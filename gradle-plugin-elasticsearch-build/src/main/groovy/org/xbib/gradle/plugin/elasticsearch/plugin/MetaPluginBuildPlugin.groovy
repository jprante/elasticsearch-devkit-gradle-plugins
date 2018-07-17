package org.xbib.gradle.plugin.elasticsearch.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.bundling.Zip
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.plugin.elasticsearch.test.RestTestPlugin
import org.xbib.gradle.plugin.elasticsearch.test.StandaloneRestTestPlugin
import org.xbib.gradle.task.elasticsearch.plugin.MetaPluginPropertiesTask
import org.xbib.gradle.task.elasticsearch.test.RunTask

class MetaPluginBuildPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(StandaloneRestTestPlugin)
        project.plugins.apply(RestTestPlugin)

        createBundleTask(project)

        project.integTestCluster {
            dependsOn(project.bundlePlugin)
            plugin(project.path)
        }
        BuildPlugin.configurePomGeneration(project)
        project.afterEvaluate {
            PluginBuildPlugin.addZipPomGeneration(project)
        }
        RunTask run = project.tasks.create('run', RunTask)
        run.dependsOn(project.bundlePlugin)
        run.clusterConfig.plugin(project.path)
    }

    private static void createBundleTask(Project project) {

        MetaPluginPropertiesTask buildProperties = project.tasks.create('pluginProperties', MetaPluginPropertiesTask.class)

        // create the actual bundle task, which zips up all the files for the plugin
        Task bundle = project.tasks.create(name: 'bundlePlugin', type: Zip, dependsOn: [buildProperties]) {
            from(buildProperties.descriptorOutput.parentFile) {
                // plugin properties file
                include(buildProperties.descriptorOutput.name)
            }
            // due to how the renames work for each bundled plugin, we must exclude empty dirs or every subdir
            // within bundled plugin zips will show up at the root as an empty dir
            includeEmptyDirs = false

        }
        project.assemble.dependsOn(bundle)

        // also make the zip available as a configuration (used when depending on this project)
        project.configurations.create('zip')
        project.artifacts.add('zip', bundle)

        // a super hacky way to inject code to run at the end of each of the bundled plugin's configuration
        // to add itself back to this meta plugin zip
        project.afterEvaluate {
            buildProperties.extension.plugins.each { String bundledPluginProjectName ->
                Project bundledPluginProject = project.project(bundledPluginProjectName)
                bundledPluginProject.afterEvaluate {
                    bundle.configure {
                        dependsOn bundledPluginProject.bundlePlugin
                        from(project.zipTree(bundledPluginProject.bundlePlugin.outputs.files.singleFile)) {
                            eachFile { FileCopyDetails details ->
                                details.relativePath = new RelativePath(true, bundledPluginProjectName,
                                                                        details.relativePath.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
