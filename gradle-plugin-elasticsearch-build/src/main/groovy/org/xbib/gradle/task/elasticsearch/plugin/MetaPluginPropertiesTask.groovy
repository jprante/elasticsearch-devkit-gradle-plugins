package org.xbib.gradle.task.elasticsearch.plugin

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputFile

class MetaPluginPropertiesTask extends Copy {

    MetaPluginPropertiesExtension extension

    @OutputFile
    File descriptorOutput = new File(project.buildDir, 'generated-resources/meta-plugin-descriptor.properties')

    MetaPluginPropertiesTask() {
        File templateFile = new File(project.buildDir, "templates/${descriptorOutput.name}")
        Task copyPluginPropertiesTemplate = project.tasks.create('copyPluginPropertiesTemplate') {
            doLast {
                InputStream resourceTemplate = PluginPropertiesTask.getResourceAsStream("/${descriptorOutput.name}")
                templateFile.parentFile.mkdirs()
                templateFile.setText(resourceTemplate.getText('UTF-8'), 'UTF-8')
            }
        }

        dependsOn(copyPluginPropertiesTemplate)
        extension = project.extensions.create('es_meta_plugin', MetaPluginPropertiesExtension, project)
        project.afterEvaluate {
            if (extension.name == null) {
                throw new InvalidUserDataException('name is a required setting for es_meta_plugin')
            }
            if (extension.description == null) {
                throw new InvalidUserDataException('description is a required setting for es_meta_plugin')
            }
            from(templateFile.parentFile).include(descriptorOutput.name)
            into(descriptorOutput.parentFile)
            Map<String, String> properties = generateSubstitutions()
            expand(properties)
            inputs.properties(properties)
        }
    }

    Map<String, String> generateSubstitutions() {
        return ['name': extension.name, 'description': extension.description]
    }
}
