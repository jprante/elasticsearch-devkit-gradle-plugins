package org.xbib.gradle.plugin.elasticsearch.plugin

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.task.elasticsearch.plugin.NoticeTask
import org.xbib.gradle.task.elasticsearch.plugin.PluginPropertiesTask
import org.xbib.gradle.task.elasticsearch.test.RestIntegTestTask
import org.xbib.gradle.task.elasticsearch.test.RunTask

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
            if (project.pluginProperties.extension.hasClientJar) {
                // for plugins which work with the transport client, we copy the jar
                // file to a new name, copy the nebula generated pom to the same name,
                // and generate a different pom for the zip
                addClientJarPomGeneration(project)
                addClientJarTask(project)
            } else {
                // no client plugin, so use the pom file from nebula, without jar, for the zip
                project.ext.set("nebulaPublish.maven.jar", false)
            }
            project.integTestCluster.dependsOn(project.bundlePlugin)
            project.tasks.run.dependsOn(project.bundlePlugin)
            if (isModule) {
                project.integTestCluster.module(project)
                project.tasks.run.clusterConfig.module(project)
            } else {
                project.integTestCluster.plugin(project.path)
                project.tasks.run.clusterConfig.plugin(project.path)
                addZipPomGeneration(project)
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
        Task bundle = project.tasks.create(name: 'bundlePlugin', type: Zip, dependsOn: [project.jar, buildProperties]) {
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
        project.assemble.dependsOn(bundle)

        // also make the zip available as a configuration (used when depending on this project)
        project.configurations.create('zip')
        project.artifacts.add('zip', bundle)
    }

    /** Adds a task to move jar and associated files to a "-client" name. */
    protected static void addClientJarTask(Project project) {
        Task clientJar = project.tasks.create('clientJar')
        clientJar.dependsOn(project.jar, project.tasks.generatePomFileForClientJarPublication, project.javadocJar, project.sourcesJar)
        clientJar.doFirst {
            Path jarFile = project.jar.outputs.files.singleFile.toPath()
            String clientFileName = jarFile.fileName.toString().replace(project.version as String, "client-${project.version}")
            Files.copy(jarFile, jarFile.resolveSibling(clientFileName), StandardCopyOption.REPLACE_EXISTING)
            String clientPomFileName = clientFileName.replace('.jar', '.pom')
            Files.copy(
                    project.tasks.generatePomFileForClientJarPublication.outputs.files.singleFile.toPath(),
                    jarFile.resolveSibling(clientPomFileName),
                    StandardCopyOption.REPLACE_EXISTING
            )
            String sourcesFileName = jarFile.fileName.toString().replace('.jar', '-sources.jar')
            String clientSourcesFileName = clientFileName.replace('.jar', '-sources.jar')
            Files.copy(jarFile.resolveSibling(sourcesFileName), jarFile.resolveSibling(clientSourcesFileName),
                    StandardCopyOption.REPLACE_EXISTING)

            if (project.compilerJavaVersion < JavaVersion.VERSION_1_10) {
                String javadocFileName = jarFile.fileName.toString().replace('.jar', '-javadoc.jar')
                String clientJavadocFileName = clientFileName.replace('.jar', '-javadoc.jar')
                Files.copy(jarFile.resolveSibling(javadocFileName), jarFile.resolveSibling(clientJavadocFileName),
                        StandardCopyOption.REPLACE_EXISTING)
            }
        }
        project.assemble.dependsOn(clientJar)
    }

    /** Adds nebula publishing task to generate a pom file for the plugin. */
    protected static void addClientJarPomGeneration(Project project) {
        project.plugins.apply(MavenPublishPlugin.class)

        project.publishing {
            publications {
                clientJar(MavenPublication) {
                    from project.components.java
                    artifactId = project.pluginProperties.extension.name + '-client'
                    pom.withXml { XmlProvider xml ->
                        Node root = xml.asNode()
                        root.appendNode('name', project.pluginProperties.extension.name)
                        root.appendNode('description', project.pluginProperties.extension.description)
                        root.appendNode('url', project.pluginProperties.extension.url)
                        Node scmNode = root.appendNode('scm')
                        scmNode.appendNode('url', project.pluginProperties.extension.url)
                    }
                }
            }
        }
    }

    /** Adds a task to generate a pom file for the zip distribution. */
    static void addZipPomGeneration(Project project) {
        project.plugins.apply(MavenPublishPlugin.class)

        project.publishing {
            publications {
                zip(MavenPublication) {
                    artifact project.bundlePlugin
                }
                /* HUGE HACK: the underlying maven publication library refuses to deploy any attached artifacts
                 * when the packaging type is set to 'pom'. But Sonatype's OSS repositories require source files
                 * for artifacts that are of type 'zip'. We already publish the source and javadoc for Elasticsearch
                 * under the various other subprojects. So here we create another publication using the same
                 * name that has the "real" pom, and rely on the fact that gradle will execute the publish tasks
                 * in alphabetical order. This lets us publish the zip file and even though the pom says the
                 * type is 'pom' instead of 'zip'. We cannot setup a dependency between the tasks because the
                 * publishing tasks are created *extremely* late in the configuration phase, so that we cannot get
                 * ahold of the actual task. Furthermore, this entire hack only exists so we can make publishing to
                 * maven local work, since we publish to maven central externally. */
                zipReal(MavenPublication) {
                    artifactId = project.pluginProperties.extension.name
                    pom.withXml { XmlProvider xml ->
                        Node root = xml.asNode()
                        root.appendNode('name', project.pluginProperties.extension.name)
                        root.appendNode('description', project.pluginProperties.extension.description)
                        root.appendNode('url', project.pluginProperties.extension.url)
                        Node scmNode = root.appendNode('scm')
                        scmNode.appendNode('url', project.pluginProperties.extension.url)
                    }
                }
            }
        }
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
