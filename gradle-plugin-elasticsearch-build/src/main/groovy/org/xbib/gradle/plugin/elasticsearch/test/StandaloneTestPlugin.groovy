package org.xbib.gradle.plugin.elasticsearch.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.plugin.randomizedtesting.RandomizedTestingTask

/**
 * Configures the build to compile against the test framework and
 * run integration and unit tests. Use BuildPlugin if you want to build main
 * code as well as tests.
 */
class StandaloneTestPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(StandaloneRestTestPlugin)
        Map testOptions = [
            name: 'test',
            type: RandomizedTestingTask,
            dependsOn: 'testClasses',
            group: JavaBasePlugin.VERIFICATION_GROUP,
            description: 'Runs unit tests that are separate'
        ]
        Task test = project.tasks.create(testOptions)
        test.configure(BuildPlugin.commonTestConfig(project))
        BuildPlugin.configureCompile(project)
        test.classpath = project.sourceSets.test.runtimeClasspath
        test.testClassesDirs = project.sourceSets.test.output.classesDirs
        test.mustRunAfter(project.precommit)
        project.check.dependsOn(test)
    }
}
