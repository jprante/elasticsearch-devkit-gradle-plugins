package org.xbib.gradle.plugin.elasticsearch.test

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.task.elasticsearch.test.RestIntegTestTask

/**
 * Adds support for starting an Elasticsearch cluster before running integration
 * tests. Used in conjunction with {@link StandaloneRestTestPlugin} for qa
 * projects and in conjunction with {@link BuildPlugin} for testing the rest
 * client.
 */
class RestTestPlugin implements Plugin<Project> {
    List REQUIRED_PLUGINS = [
        'org.xbib.gradle.plugin.elasticsearch.build',
        'org.xbib.gradle.plugin.elasticsearch.standalone-rest-test']

    @Override
    void apply(Project project) {
        if (!REQUIRED_PLUGINS.any { project.pluginManager.hasPlugin(it) }) {
            throw new InvalidUserDataException('org.xbib.gradle.plugin.elasticsearch.rest-test '
                + 'requires either org.xbib.gradle.plugin.elasticsearch.build or '
                + 'org.xbib.gradle.plugin.elasticsearch.standalone-rest-test')
        }

        RestIntegTestTask integTest = project.tasks.create('integTest', RestIntegTestTask.class)
        integTest.description = 'Runs rest tests against an Elasticsearch cluster'
        integTest.group = JavaBasePlugin.VERIFICATION_GROUP
        integTest.clusterConfig.distribution = 'zip' // rest tests should run with the real zip
        integTest.mustRunAfter(project.precommit)
        project.check.dependsOn(integTest)
    }
}
