package org.xbib.gradle.task.elasticsearch.plugin

import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputFile
import org.xbib.gradle.plugin.elasticsearch.VersionProperties

/**
 * Creates a plugin descriptor.
 */
class PluginPropertiesTask extends Copy {

    PluginPropertiesExtension extension

    @OutputFile
    File descriptorOutput = new File(project.buildDir, 'generated-resources/plugin-descriptor.properties')

    PluginPropertiesTask() {
        File templateFile = new File(project.buildDir, "templates/${descriptorOutput.name}")
        Task copyPluginPropertiesTemplate = project.tasks.create('copyPluginPropertiesTemplate') {
            doLast {
                InputStream resourceTemplate = PluginPropertiesTask.getResourceAsStream("/${descriptorOutput.name}")
                templateFile.parentFile.mkdirs()
                templateFile.setText(resourceTemplate.getText('UTF-8'), 'UTF-8')
            }
        }

        dependsOn(copyPluginPropertiesTemplate)
        extension = project.extensions.create('esplugin', PluginPropertiesExtension, project)
        project.afterEvaluate {
            if (extension.name == null) {
                logger.warn('warning: name is a required setting for esplugin')
            }
            if (extension.classname == null) {
                logger.warn('warning: classname is a required setting for esplugin')
            }
            if (extension.description == null) {
                logger.warn('warning: description is a required setting for esplugin')
            }
            if (extension.url == null) {
                logger.warn('warning: url is a required setting for esplugin')
            }
            // configure property substitution
            from(templateFile.parentFile).include(descriptorOutput.name)
            into(descriptorOutput.parentFile)
            Map<String, String> properties = generateSubstitutions()
            expand(properties)
            inputs.properties(properties)
        }
    }

    Map<String, String> generateSubstitutions() {
        def stringSnap = { version ->
            if (version.endsWith("-SNAPSHOT")) {
               return version.substring(0, version.length() - 9)
            }
            return version
        }
        return [
            'name': extension.name,
            'description': extension.description,
            'classname': extension.classname,
            'url': extension.url,
            'version': stringSnap(extension.version),
            'elasticsearchVersion': stringSnap(VersionProperties.getVersion('elasticsearch')),
            'javaVersion': project.targetCompatibility as String,
            'extendedPlugins': extension.extendedPlugins.join(','),
            'hasNativeController': Boolean.toString(extension.hasNativeController),
            'requiresKeystore': Boolean.toString(extension.requiresKeystore)
        ]
    }
}
