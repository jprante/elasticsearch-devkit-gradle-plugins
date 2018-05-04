package org.xbib.gradle.plugin.elasticsearch.vagrant

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import javax.inject.Inject

/**
 * Runs a vagrant command. Pretty much like Exec task but with a nicer output
 * formatter and defaults to `vagrant` as first part of commandLine.
 */
class VagrantCommandTask extends LoggedExec {

    @Input
    String command

    @Input @Optional
    String subcommand

    @Input
    String boxName

    @Input
    Map<String, String> environmentVars

    VagrantCommandTask() {
        executable = 'vagrant'

        // We're using afterEvaluate here to slot in some logic that captures configurations and
        // modifies the command line right before the main execution happens. The reason that we
        // call doFirst instead of just doing the work in the afterEvaluate is that the latter
        // restricts how subclasses can extend functionality. Calling afterEvaluate is like having
        // all the logic of a task happening at construction time, instead of at execution time
        // where a subclass can override or extend the logic.
        project.afterEvaluate {
            doFirst {
                if (environmentVars != null) {
                    environment environmentVars
                }

                // Build our command line for vagrant
                def vagrantCommand = [executable, command]
                if (subcommand != null) {
                    vagrantCommand = vagrantCommand + subcommand
                }
                commandLine([*vagrantCommand, boxName, *args])

                // It'd be nice if --machine-readable were, well, nice
                standardOutput = new TeeOutputStream(standardOutput, createLoggerOutputStream())
            }
        }
    }

    @Inject
    ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException()
    }

    protected OutputStream createLoggerOutputStream() {
        return new VagrantLoggerOutputStream(
            command: commandLine.join(' '),
            factory: getProgressLoggerFactory(),
            /* Vagrant tends to output a lot of stuff, but most of the important
              stuff starts with ==> $box */
            squashedPrefix: "==> $boxName: ")
    }
}
