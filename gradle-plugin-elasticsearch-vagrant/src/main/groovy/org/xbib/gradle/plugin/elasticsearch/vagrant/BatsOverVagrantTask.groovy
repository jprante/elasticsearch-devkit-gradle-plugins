package org.xbib.gradle.plugin.elasticsearch.vagrant

import org.gradle.api.tasks.Input

/**
 * Runs bats over vagrant. Pretty much like running it using Exec but with a
 * nicer output formatter.
 */
class BatsOverVagrantTask extends VagrantCommandTask {

    @Input
    String remoteCommand

    BatsOverVagrantTask() {
        command = 'ssh'
    }

    void setRemoteCommand(String remoteCommand) {
        this.remoteCommand = Objects.requireNonNull(remoteCommand)
        setArgs(['--command', remoteCommand])
    }

    @Override
    protected OutputStream createLoggerOutputStream() {
        return new TapLoggerOutputStream(
                command: commandLine.join(' '),
                factory: getProgressLoggerFactory(),
                logger: logger)
    }
}
