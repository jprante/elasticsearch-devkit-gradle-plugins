package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.xbib.elasticsearch.test.loggerusage.LoggerUsageChecker

/**
 * Runs LoggerUsageCheck.
 */
class LoggerUsageTask extends DefaultTask {

    private File successMarker = new File(project.buildDir, 'markers/loggerUsage')

    LoggerUsageTask() {
        description = "Runs LoggerUsageCheck"
        if (project.sourceSets.findByName("main")) {
            dependsOn project.tasks.classes
        }
        if (project.sourceSets.findByName("test")) {
            dependsOn project.tasks.testClasses
        }
    }

    @TaskAction
    void loggerUsageCheck() {
        List files = []
        if (project.sourceSets.findByName("main")) {
            files.add(project.sourceSets.main.output.classesDirs)
        }
        if (project.sourceSets.findByName("test")) {
            files.add(project.sourceSets.test.output.classesDirs)
        }
        List paths = []
        project.files(files).filter { it.exists() }.each {
            paths << it.getAbsolutePath()
        }
        LoggerUsageChecker checker = new LoggerUsageChecker(paths)
        checker.execute()
        successMarker.parentFile.mkdirs()
        successMarker.setText("", 'UTF-8')
    }

    @OutputFile
    File getSuccessMarker() {
        successMarker
    }
}
