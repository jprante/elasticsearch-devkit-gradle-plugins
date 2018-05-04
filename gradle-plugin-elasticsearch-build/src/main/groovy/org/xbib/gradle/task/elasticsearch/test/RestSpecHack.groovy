package org.xbib.gradle.task.elasticsearch.test

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.xbib.gradle.plugin.elasticsearch.VersionProperties

/**
 * The rest-api-spec tests are loaded from the classpath. However, they
 * currently must be available on the local filesystem. This class encapsulates
 * setting up tasks to copy the rest spec api to test resources.
 */
class RestSpecHack {
    /**
     * Sets dependencies needed to copy the rest spec.
     * @param project The project to add rest spec dependency to
     */
    static void configureDependencies(Project project) {
        project.configurations {
            restSpec
        }
        project.dependencies {
            restSpec "org.elasticsearch:rest-api-spec:${VersionProperties.getVersion('elasticsearch')}"
        }
    }

    /**
     * Creates a task (if necessary) to copy the rest spec files.
     *
     * @param project The project to add the copy task to
     * @param includePackagedTests true if the packaged tests should be copied, false otherwise
     */
    static Task configureTask(Project project, boolean includePackagedTests) {
        Task copyRestSpec = project.tasks.findByName('copyRestSpec')
        if (copyRestSpec != null) {
            return copyRestSpec
        }
        Map copyRestSpecProps = [
                name     : 'copyRestSpec',
                type     : Copy,
                dependsOn: [project.configurations.restSpec, 'processTestResources']
        ]
        copyRestSpec = project.tasks.create(copyRestSpecProps) {
            from { project.zipTree(project.configurations.restSpec.singleFile) }
            include 'rest-api-spec/api/**'
            if (includePackagedTests) {
                include 'rest-api-spec/test/**'
            }
            into project.sourceSets.test.output.resourcesDir
        }
        /*project.idea {
            module {
                if (scopes.TEST != null) {
                    scopes.TEST.plus.add(project.configurations.restSpec)
                }
            }
        }*/
        return copyRestSpec
    }
}
