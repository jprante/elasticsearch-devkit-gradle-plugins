package org.xbib.gradle.task.elasticsearch.plugin

import org.gradle.api.Project
import org.gradle.api.tasks.Input

/**
 * A container for meta plugin properties that will be written to the meta plugin descriptor, for easy
 * manipulation in the gradle DSL.
 */
class MetaPluginPropertiesExtension {
    @Input
    String name

    @Input
    String description

    /**
     * The plugins this meta plugin wraps.
     * Note this is not written to the plugin descriptor, but used to setup the final zip file task.
     */
    @Input
    List<String> plugins

    MetaPluginPropertiesExtension(Project project) {
        name = project.name
    }
}
