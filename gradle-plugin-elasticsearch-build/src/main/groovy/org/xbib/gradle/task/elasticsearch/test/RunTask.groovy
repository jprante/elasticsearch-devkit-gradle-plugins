package org.xbib.gradle.task.elasticsearch.test

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.tasks.options.Option
import org.gradle.util.ConfigureUtil

class RunTask extends DefaultTask {

    ClusterConfiguration clusterConfig

    RunTask() {
        description = "Runs elasticsearch with '${project.path}'"
        group = 'Verification'
        clusterConfig = new ClusterConfiguration(project)
        clusterConfig.httpPort = 9200
        clusterConfig.transportPort = 9300
        clusterConfig.daemonize = false
        clusterConfig.distribution = 'zip'
        project.afterEvaluate {
            ClusterFormationTasks.setup(project, name, this, clusterConfig)
        }
    }

    @Option(option = "debug-jvm",
        description = "Enable debugging configuration, to allow attaching a debugger to Elasticsearch")
    void setDebug(boolean enabled) {
        clusterConfig.debug = enabled;
    }

    /** Configure the cluster that will be run. */
    @Override
    Task configure(Closure closure) {
        ConfigureUtil.configure(closure, clusterConfig)
        return this
    }
}
