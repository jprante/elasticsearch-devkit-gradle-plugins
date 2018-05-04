package org.xbib.gradle.plugin.elasticsearch.test

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.plugin.randomizedtesting.RandomizedTestingPlugin
import org.xbib.gradle.task.elasticsearch.qa.QualityAssuranceTasks

/**
 * Configures the build to compile tests against Elasticsearch's test framework
 * and run REST tests. Use BuildPlugin if you want to build main code as well
 * as tests.
 */
class StandaloneRestTestPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (project.pluginManager.hasPlugin('org.xbib.gradle.plugin.elasticsearch.build')) {
            throw new InvalidUserDataException('org.xbib.gradle.plugin.elasticsearch.standalone-test '
                + 'org.xbib.gradle.plugin.elasticsearch.standalone-rest-test, and org.xbib.gradle.plugin.elasticsearch.build '
                + 'are mutually exclusive')
        }
        project.pluginManager.apply(JavaBasePlugin)
        project.pluginManager.apply(RandomizedTestingPlugin)

        BuildPlugin.globalBuildInfo(project)
        BuildPlugin.configureRepositories(project)

        // only setup tests to build
        project.sourceSets.create('test')
        project.dependencies.add('testCompile',
                "org.xbib.elasticsearch:elasticsearch-test-framework:${project.property('xbib-elasticsearch-test.version')}")


        project.eclipse.classpath.sourceSets = [project.sourceSets.test]
        project.eclipse.classpath.plusConfigurations = [project.configurations.testRuntime]
        project.idea.module.testSourceDirs += project.sourceSets.test.java.srcDirs
        project.idea.module.scopes['TEST'] = [plus: [project.configurations.testRuntime]]

        QualityAssuranceTasks.create(project, false)
        project.check.dependsOn(project.precommit)
    }
}
