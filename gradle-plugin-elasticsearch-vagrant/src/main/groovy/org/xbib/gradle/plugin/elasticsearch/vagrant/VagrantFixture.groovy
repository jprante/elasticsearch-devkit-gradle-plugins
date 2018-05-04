package org.xbib.gradle.plugin.elasticsearch.vagrant

import org.gradle.api.Task

/**
 * A fixture for integration tests which runs in a virtual machine launched by Vagrant.
 */
class VagrantFixture extends VagrantCommandTask implements Fixture {

    private VagrantCommandTask stopTask

    VagrantFixture() {
        this.stopTask = project.tasks.create(name: "${name}#stop", type: VagrantCommandTask) {
            command 'halt'
        }
        finalizedBy this.stopTask
    }

    @Override
    void setBoxName(String boxName) {
        super.setBoxName(boxName)
        this.stopTask.setBoxName(boxName)
    }

    @Override
    void setEnvironmentVars(Map<String, String> environmentVars) {
        super.setEnvironmentVars(environmentVars)
        this.stopTask.setEnvironmentVars(environmentVars)
    }

    @Override
    Task getStopTask() {
        return this.stopTask
    }
}
