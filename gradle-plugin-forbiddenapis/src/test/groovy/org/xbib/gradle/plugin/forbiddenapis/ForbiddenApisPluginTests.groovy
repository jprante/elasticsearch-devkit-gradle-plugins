package org.xbib.gradle.plugin.forbiddenapis

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Specification

class ForbiddenApisPluginTests extends Specification {

    Project project

    File projectDir

    File buildFile

    def setup() {
        projectDir = File.createTempDir()
        buildFile = file('build.gradle')
        project = ProjectBuilder.builder().build()
    }

    def 'apply plugin'() {
        given:
        String projectName = 'myproject'
        String version = '1.0.0'
        Project project = ProjectBuilder.builder()
                .withName(projectName)
                .build()
        project.version = version

        when:
        project.plugins.apply(ForbiddenApisPlugin)

        then:
        project.plugins.hasPlugin(ForbiddenApisPlugin)
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