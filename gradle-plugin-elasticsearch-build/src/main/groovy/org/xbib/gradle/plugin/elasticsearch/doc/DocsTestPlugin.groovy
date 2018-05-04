package org.xbib.gradle.plugin.elasticsearch.doc

import org.gradle.api.Project
import org.gradle.api.Task
import org.xbib.gradle.plugin.elasticsearch.VersionProperties
import org.xbib.gradle.plugin.elasticsearch.test.RestTestPlugin
import org.xbib.gradle.task.elasticsearch.doc.RestTestsFromSnippetsTask
import org.xbib.gradle.task.elasticsearch.doc.SnippetsTask

/**
 * Sets up tests for documentation.
 */
public class DocsTestPlugin extends RestTestPlugin {

    @Override
    public void apply(Project project) {
        project.pluginManager.apply('elasticsearch.standalone-rest-test')
        super.apply(project)
        // Docs are published separately so no need to assemble
        project.tasks.remove(project.assemble)
        project.build.dependsOn.remove('assemble')
        Map<String, String> defaultSubstitutions = [
            /* These match up with the asciidoc syntax for substitutions but
             * the values may differ. In particular {version} needs to resolve
             * to the version being built for testing but needs to resolve to
             * the last released version for docs. */
            '\\{version\\}':
                VersionProperties.getVersion('elasticsearch').replace('-SNAPSHOT', ''),
            '\\{lucene_version\\}' : VersionProperties.getVersion('lucene').replaceAll('-snapshot-\\w+$', ''),
        ]
        Task listSnippets = project.tasks.create('listSnippets', SnippetsTask)
        listSnippets.group 'Docs'
        listSnippets.description 'List each snippet'
        listSnippets.defaultSubstitutions = defaultSubstitutions
        listSnippets.perSnippet { println(it.toString()) }

        Task listConsoleCandidates = project.tasks.create(
                'listConsoleCandidates', SnippetsTask)
        listConsoleCandidates.group 'Docs'
        listConsoleCandidates.description
                'List snippets that probably should be marked // CONSOLE'
        listConsoleCandidates.defaultSubstitutions = defaultSubstitutions
        listConsoleCandidates.perSnippet {
            if (RestTestsFromSnippetsTask.isConsoleCandidate(it)) {
                println(it.toString())
            }
        }

        Task buildRestTests = project.tasks.create(
                'buildRestTests', RestTestsFromSnippetsTask)
        buildRestTests.defaultSubstitutions = defaultSubstitutions
    }
}
