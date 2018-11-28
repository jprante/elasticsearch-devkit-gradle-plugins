package org.xbib.gradle.task.elasticsearch.test

import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec

/**
 * A wrapper around gradle's Exec task to capture output and log on error.
 */
class LoggedExec extends Exec {

    protected ByteArrayOutputStream output = new ByteArrayOutputStream()

    LoggedExec() {
        environment('JAVA_HOME', System.getProperty('java.home'))
        if (!logger.isInfoEnabled()) {
            standardOutput = output
            errorOutput = output
            ignoreExitValue = true
            doLast {
                if (execResult.exitValue != 0) {
                    output.toString('UTF-8').eachLine { line -> logger.error(line) }
                    throw new GradleException("Process '${executable} ${args.join(' ')}' finished with non-zero exit value ${execResult.exitValue}")
                }
            }
        }
    }
}
