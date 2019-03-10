package org.xbib.gradle.plugin.elasticsearch

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import spock.lang.Specification

class BuildPluginTests extends Specification {

    File projectDir
    File buildFile

    def setup() {
        projectDir = File.createTempDir()
        buildFile = file('build.gradle')
    }

    def 'apply plugin'() {
        given:
        String projectName = 'mybuild'
        String version = '1.0.0'
        Project project = ProjectBuilder.builder()
                .withName(projectName)
                .build()
        project.version = version
        project.ext['elasticsearch.version'] = '6.3.2'
        project.ext['elasticsearch-devkit.version'] = '6.3.2.6'
        project.ext['elasticsearch-server.version'] = '6.3.2.2'
        project.ext['checkstyle.version'] = '8.13'

        when:
        project.plugins.apply(BuildPlugin)

        then:
        project.plugins.hasPlugin(BuildPlugin)
    }

    private BuildResult build(String... args) {
        return GradleRunner.create()
                .withGradleVersion(GradleVersion.current().version)
                .withPluginClasspath()
                .withProjectDir(projectDir)
                .forwardOutput()
                .withArguments((args + '--stacktrace') as String[])
                .withDebug(true)
                .build()
    }

    protected File file(String path, File baseDir = projectDir) {
        def splitted = path.split('/')
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join('/'), baseDir) : baseDir
        new File(directory as File, splitted[-1])
    }

    protected File directory(String path, File baseDir = projectDir) {
        return new File(baseDir, path).with {
            mkdirs()
            return it
        }
    }
}