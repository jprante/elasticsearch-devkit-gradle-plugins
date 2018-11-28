package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile

/**
 * Runs LoggerUsageCheck on a set of directories.
 */
class OldLoggerUsageTask extends LoggedResultJavaExec {

    /**
     * We use a simple "marker" file that we touch when the task succeeds
     * as the task output. This is compared against the modified time of the
     * inputs (ie the jars/class files).
     */
    private File successMarker = new File(project.buildDir, 'markers/loggerUsage')

    private FileCollection classpath

    private FileCollection classDirectories;

    OldLoggerUsageTask() {
        project.afterEvaluate {
            dependsOn(classpath)
            description = "Runs LoggerUsageCheck on ${classDirectories}"
            //executable = new File(project.runtimeJavaHome, 'bin/java')
            if (classDirectories == null) {
                // Default to main and test class files
                List files = []
                // But only if the source sets that will make them exist
                if (project.sourceSets.findByName("main")) {
                    files.add(project.sourceSets.main.output.classesDirs)
                    dependsOn project.tasks.classes
                }
                if (project.sourceSets.findByName("test")) {
                    files.add(project.sourceSets.test.output.classesDirs)
                    dependsOn project.tasks.testClasses
                }
                /* In an extra twist, it isn't good enough that the source set
                 * exists. Empty source sets won't make a classes directory
                 * which will cause the check to fail. We have to filter the
                 * empty directories out manually. This filter is done right
                 * before the actual logger usage check giving the rest of the
                 * build the opportunity to actually build the directory.
                 */
                classDirectories = project.files(files).filter { it.exists() }
            }
            doFirst({
                classpath getClasspath()
                main = 'org.elasticsearch.test.loggerusage.ESLoggerUsageChecker'
                getClassDirectories().each {
                    args it.getAbsolutePath()
                }
            })
            doLast({
                successMarker.parentFile.mkdirs()
                successMarker.setText("", 'UTF-8')
            })
        }
    }

    @InputFiles
    FileCollection getClasspath() {
        return classpath
    }

    @Override
    ResultJavaExec setClasspath(FileCollection classpath) {
        super.setClasspath(classpath)
        this.classpath = classpath
        this
    }

    @InputFiles
    FileCollection getClassDirectories() {
        return classDirectories
    }

    void setClassDirectories(FileCollection classDirectories) {
        this.classDirectories = classDirectories
    }

    @OutputFile
    File getSuccessMarker() {
        return successMarker
    }

    void setSuccessMarker(File successMarker) {
        this.successMarker = successMarker
    }
}
