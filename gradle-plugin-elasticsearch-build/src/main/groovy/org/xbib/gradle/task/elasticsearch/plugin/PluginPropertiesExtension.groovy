package org.xbib.gradle.task.elasticsearch.plugin

import org.gradle.api.Project
import org.gradle.api.tasks.Input

/**
 * A container for plugin properties that will be written to the plugin descriptor, for easy
 * manipulation in the gradle DSL.
 */
class PluginPropertiesExtension {

    @Input
    String name

    @Input
    String version

    @Input
    String description

    @Input
    String classname

    @Input
    String url
    
    /** Other plugins this plugin extends through SPI */
    @Input
    List<String> extendedPlugins = []

    @Input
    boolean hasNativeController = false

    /** Indicates whether the plugin jar should be made available for the transport client. */
    @Input
    boolean hasClientJar = false

    /** True if the plugin requires the elasticsearch keystore to exist, false otherwise. */
    @Input
    boolean requiresKeystore = false

    /** A license file that should be included in the built plugin zip. */
    @Input
    File licenseFile = null

    /**
     * A notice file that should be included in the built plugin zip. This will be
     * extended with notices from the {@code licenses/} directory.
     */
    @Input
    File noticeFile = null

    PluginPropertiesExtension(Project project) {
        name = project.name
        version = project.version
    }
}
