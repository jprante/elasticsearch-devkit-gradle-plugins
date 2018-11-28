package org.xbib.gradle.task.elasticsearch.test

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskState
import org.xbib.gradle.plugin.elasticsearch.build.BuildPlugin
import org.xbib.gradle.plugin.randomizedtesting.RandomizedTestingTask

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.stream.Stream

/**
 * A wrapper task around setting up a cluster and running rest tests.
 */
class RestIntegTestTask extends DefaultTask {

    protected ClusterConfiguration clusterConfig

    protected RandomizedTestingTask runner

    protected Task clusterInit

    /** Info about nodes in the integ test cluster. Note this is *not* available until runtime. */
    List<NodeInfo> nodes

    /** Flag indicating whether the rest tests in the rest spec should be run. */
    @Input
    boolean includePackaged = false

    RestIntegTestTask() {
        runner = project.tasks.create("${name}Runner", RandomizedTestingTask)
        super.dependsOn(runner)
        clusterInit = project.tasks.create(name: "${name}Cluster#init", dependsOn: project.testClasses)
        runner.dependsOn(clusterInit)
        runner.modulepath = project.sourceSets.test.runtimeClasspath
        runner.testClassesDirs = project.sourceSets.test.output.classesDirs
        clusterConfig = project.extensions.create("${name}Cluster", ClusterConfiguration, project) as ClusterConfiguration
        clusterConfig.environment('JAVA_HOME', System.getProperty('java.home'))

        // start with the common test configuration
        runner.configure(BuildPlugin.commonTestConfig(project))
        // override/add more for rest tests
        runner.parallelism = '1'
        runner.include('**/*IT.class')
        runner.systemProperty('tests.rest.load_packaged', 'false')
        // we pass all nodes to the rest cluster to allow the clients to round-robin between them
        // this is more realistic than just talking to a single node
        runner.systemProperty('tests.rest.cluster', "${-> nodes.collect{it.httpUri()}.join(",")}")
        runner.systemProperty('tests.config.dir', "${-> nodes[0].pathConf}")
        // TODO: our "client" qa tests currently use the rest-test plugin. instead they should have their own plugin
        // that sets up the test cluster and passes this transport uri instead of http uri. Until then, we pass
        // both as separate sysprops
        runner.systemProperty('tests.cluster', "${-> nodes[0].transportUri()}")

        // dump errors and warnings from cluster log on failure
        TaskExecutionAdapter logDumpListener = new TaskExecutionAdapter() {
            @Override
            void afterExecute(Task task, TaskState state) {
                if (state.failure != null) {
                    for (NodeInfo nodeInfo : nodes) {
                        printLogExcerpt(nodeInfo)
                    }
                }
            }
        }
        runner.doFirst {
            project.gradle.addListener(logDumpListener)
        }
        runner.doLast {
            project.gradle.removeListener(logDumpListener)
        }

        // copy the rest spec/tests into the test resources
        RestSpecHack.configureDependencies(project)
        project.afterEvaluate {
            runner.dependsOn(RestSpecHack.configureTask(project, includePackaged))
        }
        // this must run after all projects have been configured, so we know any project
        // references can be accessed as a fully configured
        project.gradle.projectsEvaluated {
            if (!enabled) {
                runner.enabled = false
                clusterInit.enabled = false
                return // no need to add cluster formation tasks if the task won't run!
            }
            nodes = ClusterFormationTasks.setup(project, "${name}Cluster", runner, clusterConfig)
            super.dependsOn(runner.finalizedBy)
        }
    }

    @Option(option = "debug-jvm",
        description = "Enable debugging configuration, to allow attaching a debugger to Elasticsearch")
    void setDebug(boolean enabled) {
        clusterConfig.debug = enabled;
    }

    List<NodeInfo> getNodes() {
        return nodes
    }

    @Override
    Task dependsOn(Object... dependencies) {
        runner.dependsOn(dependencies)
        for (Object dependency : dependencies) {
            if (dependency instanceof Fixture) {
                runner.finalizedBy(((Fixture)dependency).getStopTask())
            }
        }
        return this
    }

    @Override
    void setDependsOn(Iterable<?> dependencies) {
        runner.setDependsOn(dependencies)
        for (Object dependency : dependencies) {
            if (dependency instanceof Fixture) {
                runner.finalizedBy(((Fixture)dependency).getStopTask())
            }
        }
    }

    @Override
    Task mustRunAfter(Object... tasks) {
        clusterInit.mustRunAfter(tasks)
    }

    /** Print out an excerpt of the log from the given node. */
    protected static void printLogExcerpt(NodeInfo nodeInfo) {
        File logFile = new File(nodeInfo.homeDir, "logs/${nodeInfo.clusterName}.log")
        println("\nCluster ${nodeInfo.clusterName} - node ${nodeInfo.nodeNum} log excerpt:")
        println("(full log at ${logFile})")
        println('-----------------------------------------')
        Stream<String> stream = Files.lines(logFile.toPath(), StandardCharsets.UTF_8)
        try {
            boolean inStartup = true
            boolean inExcerpt = false
            int linesSkipped = 0
            for (String line : stream) {
                if (line.startsWith("[")) {
                    inExcerpt = false // clear with the next log message
                }
                if (line =~ /(\[WARN *\])|(\[ERROR *\])/) {
                    inExcerpt = true // show warnings and errors
                }
                if (inStartup || inExcerpt) {
                    if (linesSkipped != 0) {
                        println("... SKIPPED ${linesSkipped} LINES ...")
                    }
                    println(line)
                    linesSkipped = 0
                } else {
                    ++linesSkipped
                }
                if (line =~ /recovered \[\d+\] indices into cluster_state/) {
                    inStartup = false
                }
            }
        } finally {
            stream.close()
        }
        println('=========================================')
    }
}
