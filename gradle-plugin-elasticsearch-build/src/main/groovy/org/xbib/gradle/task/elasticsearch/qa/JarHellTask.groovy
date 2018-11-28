package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.OutputFile

/**
 * Runs CheckJarHell on a classpath.
 */
class JarHellTask extends LoggedResultJavaExec {

    /**
     * We use a simple "marker" file that we touch when the task succeeds
     * as the task output. This is compared against the modified time of the
     * inputs (ie the jars/class files).
     */
    @OutputFile
    File successMarker = new File(project.buildDir, 'markers/jarHell')

    JarHellTask() {
        project.afterEvaluate {
            FileCollection fileCollection = project.sourceSets.test.runtimeClasspath
            inputs.files(fileCollection)
            dependsOn(fileCollection)
            description = "Runs JarHell check"
            doFirst({
                /* JarHell doesn't like getting directories that don't exist but
                  gradle isn't especially careful about that. So we have to do it
                  filter it ourselves. */
                FileCollection taskClasspath = fileCollection.filter { it.exists() }
                classpath taskClasspath.asPath
                main = 'org.elasticsearch.bootstrap.JarHell'
            })
            doLast({
                successMarker.parentFile.mkdirs()
                successMarker.setText("", 'UTF-8')
            })
        }
    }
}
