package org.xbib.gradle.plugin.elasticsearch.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.task.elasticsearch.plugin.NoticeTask
import org.xbib.gradle.task.elasticsearch.plugin.PluginPropertiesTask
import org.xbib.gradle.task.elasticsearch.test.RestIntegTestTask
import org.xbib.gradle.task.elasticsearch.test.RunTask

/**
 * Encapsulates build configuration for an Elasticsearch plugin.
 */
class PluginBuildPlugin extends BuildPlugin {

    @Override
    void apply(Project project) {
        super.apply(project)
        configureDependencies(project)
        // this afterEvaluate must happen before the afterEvaluate added by integTest creation,
        // so that the file name resolution for installing the plugin will be setup
        project.afterEvaluate {
            boolean isModule = project.path.startsWith(':modules:')
            String name = project.pluginProperties.extension.name
            project.archivesBaseName = name
            project.integTestCluster.dependsOn(project.bundlePlugin)
            project.tasks.run.dependsOn(project.bundlePlugin)
            if (isModule) {
                project.integTestCluster.module(project)
                project.tasks.run.clusterConfig.module(project)
            } else {
                project.integTestCluster.plugin(project.path)
                project.tasks.run.clusterConfig.plugin(project.path)
                addNoticeGeneration(project)
            }
            project.namingConventions {
                // Plugins declare integration tests as "Tests" instead of IT.
                skipIntegTestInDisguise = true
            }
        }
        createIntegTestTask(project)
        createBundleTask(project)
        project.configurations.getByName('default').extendsFrom(project.configurations.getByName('runtime'))
        project.tasks.create('run', RunTask) // allow running ES with this plugin in the foreground of a build
    }

    private static void configureDependencies(Project project) {
        project.dependencies {
            provided "org.xbib.elasticsearch:elasticsearch:${project.property('elasticsearch-server.version')}"
            provided "org.xbib.elasticsearch:spatial4j:${project.property('elasticsearch-libs.version')}"
            provided "org.xbib.elasticsearch:jts:${project.property('elasticsearch-libs.version')}"
            provided "org.xbib.elasticsearch:log4j:${project.property('elasticsearch-libs.version')}"
            provided "org.xbib.elasticsearch:jna:${project.property('elasticsearch-libs.version')}"
            compileOnly "org.xbib.elasticsearch:elasticsearch:${project.property('elasticsearch-server.version')}"
            testCompile "org.xbib.elasticsearch:elasticsearch-test-framework:${project.property('elasticsearch-devkit.version')}"
        }
    }

    /** Adds an integTest task which runs rest tests */
    private static void createIntegTestTask(Project project) {
        RestIntegTestTask integTest = project.tasks.create('integTest', RestIntegTestTask.class)
        integTest.mustRunAfter(project.precommit, project.test)
        project.check.dependsOn(integTest)
    }

    /**
     * Adds a bundlePlugin task which builds the zip containing the plugin jars,
     * metadata, properties, and packaging files
     */
    private static void createBundleTask(Project project) {
        File pluginMetadata = project.file('src/main/plugin-metadata')

        // create a task to build the properties file for this plugin
        PluginPropertiesTask buildProperties = project.tasks.create('pluginProperties', PluginPropertiesTask.class)

        // add the plugin properties and metadata to test resources, so unit tests can
        // know about the plugin (used by test security code to statically initialize the plugin in unit tests)
        SourceSet testSourceSet = project.sourceSets.test
        testSourceSet.output.dir(buildProperties.descriptorOutput.parentFile, builtBy: 'pluginProperties')
        testSourceSet.resources.srcDir(pluginMetadata)

        // create the actual bundle task, which zips up all the files for the plugin
        Task zip = project.tasks.create(name: 'bundlePlugin', type: Zip, dependsOn: [project.jar, buildProperties]) {
            from(buildProperties.descriptorOutput.parentFile) {
                // plugin properties file
                include(buildProperties.descriptorOutput.name)
            }
            from pluginMetadata // metadata (eg custom security policy)
            from project.jar // this plugin's jar
            from project.configurations.runtime - project.configurations.provided // without provided jars
            // extra files for the plugin to go into the zip
            from('src/main/packaging')
            from('src/main') {
                include 'config/**'
                include 'bin/**'
            }
        }
        project.assemble.dependsOn(zip)
        // also make the zip available as a configuration (used when depending on this project)
        project.configurations.create('zip')
        project.artifacts.add('zip', zip)
    }

    protected void addNoticeGeneration(Project project) {
        File licenseFile = project.pluginProperties.extension.licenseFile
        if (licenseFile != null) {
            project.bundlePlugin.from(licenseFile.parentFile) {
                include(licenseFile.name)
            }
        }
        File noticeFile = project.pluginProperties.extension.noticeFile
        if (noticeFile != null) {
            NoticeTask generateNotice = project.tasks.create('generateNotice', NoticeTask.class)
            generateNotice.inputFile = noticeFile
            project.bundlePlugin.from(generateNotice)
        }
    }
}
