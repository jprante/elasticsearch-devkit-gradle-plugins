package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.GradleException

/**
 * A wrapper around Gradle's Exec task to capture output and log on error.
 */
class LoggedResultJavaExec extends ResultJavaExec {

    protected ByteArrayOutputStream output = new ByteArrayOutputStream()

    LoggedResultJavaExec() {
        if (!logger.isInfoEnabled()) {
            standardOutput = output
            errorOutput = output
            doLast {
                if (execResult.exitValue != 0) {
                    output.toString('UTF-8').eachLine { line -> logger.error(line) }
                    throw new GradleException("Process '${executable} ${args.join(' ')}' finished with non-zero exit value ${execResult.exitValue}")
                }
            }
        }
    }
}
