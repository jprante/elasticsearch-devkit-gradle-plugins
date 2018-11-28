package org.xbib.gradle.task.elasticsearch.test

import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.apache.tools.ant.DefaultLogger
import org.gradle.api.AntBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.xbib.gradle.plugin.elasticsearch.Version
import org.xbib.gradle.plugin.elasticsearch.VersionProperties
import org.xbib.gradle.plugin.elasticsearch.plugin.MetaPluginBuildPlugin
import org.xbib.gradle.plugin.elasticsearch.plugin.PluginBuildPlugin
import org.xbib.gradle.task.elasticsearch.plugin.PluginPropertiesExtension

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * A helper for creating tasks to build a cluster that is used by a task, and tear down the cluster when the task is finished.
 */
class ClusterFormationTasks {

    private static final Logger logger = LogManager.getLogger(ClusterFormationTasks)

    /**
     * Adds dependent tasks to the given task to start and stop a cluster with the given configuration.
     *
     * Returns a list of NodeInfo objects for each node in the cluster.
     */
    static List<NodeInfo> setup(Project project, String prefix, Task runner, ClusterConfiguration config) {
        File sharedDir = new File(project.buildDir, "cluster/shared")
        Object startDependencies = config.dependencies
        /* First, if we want a clean environment, we remove everything in the
         * shared cluster directory to ensure there are no leftovers in repos
         * or anything in theory this should not be necessary but repositories
         * are only deleted in the cluster-state and not on-disk such that
         * snapshots survive failures / test runs and there is no simple way
         * today to fix that. */
        if (config.cleanShared) {
          Task cleanup = project.tasks.create(
            name: "${prefix}#prepareCluster.cleanShared",
            type: Delete,
            dependsOn: startDependencies) {
              delete sharedDir
              doLast {
                  sharedDir.mkdirs()
              }
          }
          startDependencies = cleanup
        }
        List<Task> startTasks = []
        List<NodeInfo> nodes = []
        if (config.numNodes < config.numBwcNodes) {
            throw new GradleException("numNodes must be >= numBwcNodes [${config.numNodes} < ${config.numBwcNodes}]")
        }
        if (config.numBwcNodes > 0 && config.bwcVersion == null) {
            throw new GradleException("bwcVersion must not be null if numBwcNodes is > 0")
        }
        // this is our current version distribution configuration we use for all kinds of REST tests etc.
        Configuration currentDistro = project.configurations.create("${prefix}_elasticsearchDistro")
        Configuration bwcDistro = project.configurations.create("${prefix}_elasticsearchBwcDistro")
        Configuration bwcPlugins = project.configurations.create("${prefix}_elasticsearchBwcPlugins")
        configureDistributionDependency(project, config.distribution, currentDistro,
                VersionProperties.getVersion('elasticsearch'))
        if (config.numBwcNodes > 0) {
            if (config.bwcVersion == null) {
                throw new IllegalArgumentException("Must specify bwcVersion when numBwcNodes > 0")
            }
            // if we have a cluster that has a BWC cluster we also need to configure a dependency on the BWC version
            // this version uses the same distribution etc. and only differs in the version we depend on.
            // from here on everything else works the same as if it's the current version, we fetch the BWC version
            // from mirrors using gradles built-in mechanism etc.

            configureDistributionDependency(project, config.distribution, bwcDistro, config.bwcVersion)
            for (Map.Entry<String, Project> entry : config.plugins.entrySet()) {
                configureBwcPluginDependency("${prefix}_elasticsearchBwcPlugins", project, entry.getValue(), bwcPlugins, config.bwcVersion)
            }
            bwcDistro.resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
            bwcPlugins.resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
        }
        for (int i = 0; i < config.numNodes; i++) {
            // we start N nodes and out of these N nodes there might be M bwc nodes.
            // for each of those nodes we might have a different configuration
            String elasticsearchVersion = VersionProperties.getVersion('elasticsearch')
            Configuration distro = currentDistro
            if (i < config.numBwcNodes) {
                elasticsearchVersion = config.bwcVersion
                distro = bwcDistro
            }
            NodeInfo node = new NodeInfo(config, i, project, prefix, elasticsearchVersion, sharedDir)
            nodes.add(node)
            Object dependsOn = startTasks.empty ? startDependencies : startTasks.get(0)
            startTasks.add(configureNode(project, prefix, runner, dependsOn, node, config, distro, nodes.get(0)))
        }

        Task wait = configureWaitTask("${prefix}#wait", project, nodes, startTasks, config.nodeStartupWaitSeconds)
        runner.dependsOn(wait)
        nodes
    }

    /** Adds a dependency on the given distribution */
    static void configureDistributionDependency(Project project, String distro, Configuration configuration, String version) {
        String packaging = distro
        if (distro == 'tar') {
            packaging = 'tar.gz'
        } else if (distro == 'integ-test-tar') {
            packaging = 'tar.gz'
        } else if (distro == 'integ-test-zip') {
            packaging = 'zip'
        }
        project.dependencies.add(configuration.name,
                "org.xbib.elasticsearch:elasticsearch:${version}:${packaging}")
    }

    /** Adds a dependency on a different version of the given plugin, which will be retrieved using gradle's dependency resolution */
    static void configureBwcPluginDependency(String name, Project project, Project pluginProject,
                                             Configuration configuration, String version) {
        verifyProjectHasBuildPlugin(name, version, project, pluginProject)
        String pluginName = findPluginName(pluginProject)
        project.dependencies.add(configuration.name,
                "org.xbib.elasticsearch.plugin:${pluginName}:${version}@zip")
    }

    /**
     * Adds dependent tasks to start an elasticsearch cluster before the given task is executed,
     * and stop it after it has finished executing.
     *
     * The setup of the cluster involves the following:
     * <ol>
     *   <li>Cleanup the extraction directory</li>
     *   <li>Extract a fresh copy of elasticsearch</li>
     *   <li>Write an elasticsearch.yml config file</li>
     *   <li>Copy plugins that will be installed to a temporary dir (which contains spaces)</li>
     *   <li>Install plugins</li>
     *   <li>Run additional setup commands</li>
     *   <li>Start elasticsearch<li>
     * </ol>
     *
     * @return a task which starts the node.
     */
    static Task configureNode(Project project, String prefix, Task runner, Object dependsOn, NodeInfo node, ClusterConfiguration config,
                              Configuration distribution, NodeInfo seedNode) {

        // tasks are chained so their execution order is maintained
        Task setup = project.tasks.create(name: taskName(prefix, node, 'clean'), type: Delete, dependsOn: dependsOn) {
            delete node.homeDir
            delete node.cwd
        }
        setup = project.tasks.create(name: taskName(prefix, node, 'createCwd'), type: DefaultTask, dependsOn: setup) {
            doLast {
                node.cwd.mkdirs()
            }
            outputs.dir node.cwd
        }
        setup = configureCheckPreviousTask(taskName(prefix, node, 'checkPrevious'), project, setup, node)
        setup = configureStopTask(taskName(prefix, node, 'stopPrevious'), project, setup, node)
        setup = configureExtractTask(taskName(prefix, node, 'extract'), project, setup, node, distribution)
        setup = configureWriteConfigTask(taskName(prefix, node, 'configure'), project, setup, node, seedNode)
        setup = configureJvmOptions(taskName(prefix, node, 'jvmOptions'), project, setup, node)
        setup = configureCreateKeystoreTask(taskName(prefix, node, 'createKeystore'), project, setup, node)
        setup = configureAddKeystoreSettingTasks(prefix, project, setup, node)

        if (!node.config.plugins.isEmpty()) {
            if (node.nodeVersion == VersionProperties.getVersion('elasticsearch')) {
                setup = configureCopyPluginsTask(taskName(prefix, node, 'copyPlugins'), project, setup, node, prefix)
            } else {
                setup = configureCopyBwcPluginsTask(taskName(prefix, node, 'copyBwcPlugins'), project, setup, node, prefix)
            }
        }

        for (Project module : node.config.modules) {
            String actionName = pluginTaskName('install', module.name, 'Module')
            setup = configureInstallModuleTask(taskName(prefix, node, actionName), project, setup, node, module)
        }

        for (Map.Entry<String, Project> plugin : node.config.plugins.entrySet()) {
            String actionName = pluginTaskName('install', plugin.getKey(), 'Plugin')
            setup = configureInstallPluginTask(taskName(prefix, node, actionName), project, setup, node, plugin.getValue(), prefix)
        }

        // sets up any extra config files that need to be copied over to the ES instance;
        // its run after plugins have been installed, as the extra config files may belong to plugins
        setup = configureExtraConfigFilesTask(taskName(prefix, node, 'extraConfig'), project, setup, node)

        // extra setup commands
        for (Map.Entry<String, Object[]> command : node.config.setupCommands.entrySet()) {
            // the first argument is the actual script name, relative to home
            Object[] args = command.getValue().clone()
            Object commandPath
            commandPath = node.homeDir.toPath().resolve(args[0].toString()).toString()
            args[0] = commandPath
            setup = configureExecTask(taskName(prefix, node, command.getKey()), project, setup, node, args)
        }

        Task start = configureStartTask(taskName(prefix, node, 'start'), project, setup, node)

        if (node.config.daemonize) {
            Task stop = configureStopTask(taskName(prefix, node, 'stop'), project, [], node)
            // if we are running in the background, make sure to stop the server when the task completes
            runner.finalizedBy(stop)
            start.finalizedBy(stop)
            for (Object dependency : config.dependencies) {
                if (dependency instanceof Fixture) {
                    def depStop = ((Fixture)dependency).stopTask
                    runner.finalizedBy(depStop)
                    start.finalizedBy(depStop)
                }
            }
        }
        start
    }

    /** Adds a task to extract the elasticsearch distribution */
    static Task configureExtractTask(String name, Project project, Task setup, NodeInfo node, Configuration configuration) {
        List extractDependsOn = [configuration, setup]
        /* configuration.singleFile will be an external artifact if this is being run by a plugin not living in the
          elasticsearch source tree. If this is a plugin built in the elasticsearch source tree or this is a distro in
          the elasticsearch source tree then this should be the version of elasticsearch built by the source tree.
          If it isn't then Bad Things(TM) will happen. */
        Task extract

        switch (node.config.distribution) {
            case 'integ-test-zip':
            case 'zip':
                extract = project.tasks.create(name: name, type: Copy, dependsOn: extractDependsOn) {
                    from {
                        project.zipTree(configuration.singleFile)
                    }
                    into node.baseDir
                }
                break
            case 'integ-test-tar':
            case 'tar':
                extract = project.tasks.create(name: name, type: Copy, dependsOn: extractDependsOn) {
                    from {
                        project.tarTree(project.resources.gzip(configuration.singleFile))
                    }
                    into node.baseDir
                }
                break
            default:
                throw new InvalidUserDataException("Unknown distribution: ${node.config.distribution}")
        }
        extract
    }

    /** Adds a task to write elasticsearch.yml for the given node configuration */
    static Task configureWriteConfigTask(String name, Project project, Task setup, NodeInfo node, NodeInfo seedNode) {
        Map esConfig = [
                'cluster.name'                 : node.clusterName,
                'node.name'                    : "node-" + node.nodeNum,
                'pidfile'                      : node.pidFile,
                'path.repo'                    : "${node.sharedDir}/repo",
                'path.shared_data'             : "${node.sharedDir}/",
                // Define a node attribute so we can test that it exists
                'node.attr.testattr'           : 'test'
        ]
        int minimumMasterNodes = node.config.minimumMasterNodes.call()
        if (minimumMasterNodes > 0) {
            esConfig['discovery.zen.minimum_master_nodes'] = minimumMasterNodes
        }
        if (node.config.numNodes > 1) {
            // don't wait for state.. just start up quickly
            // this will also allow new and old nodes in the BWC case to become the master
            esConfig['discovery.initial_state_timeout'] = '0s'
        }
        esConfig['node.max_local_storage_nodes'] = node.config.numNodes
        esConfig['http.port'] = node.config.httpPort
        esConfig['transport.tcp.port'] =  node.config.transportPort
        // Default the watermarks to absurdly low to prevent the tests from failing on nodes without enough disk space
        esConfig['cluster.routing.allocation.disk.watermark.low'] = '1b'
        esConfig['cluster.routing.allocation.disk.watermark.high'] = '1b'
        if (Version.fromString(node.nodeVersion).major >= 6) {
            esConfig['cluster.routing.allocation.disk.watermark.flood_stage'] = '1b'
        }
        // increase script compilation limit since tests can rapid-fire script compilations
        esConfig['script.max_compilations_rate'] = '2048/1m'
        esConfig.putAll(node.config.settings as Map)

        Task writeConfig = project.tasks.create(name: name, type: DefaultTask, dependsOn: setup)
        writeConfig.doFirst {
            String unicastTransportUri = node.config.unicastTransportUri(seedNode, node, project.ant)
            if (unicastTransportUri != null) {
                esConfig['discovery.zen.ping.unicast.hosts'] = "\"${unicastTransportUri}\""
            }
            File configFile = new File(node.pathConf, 'elasticsearch.yml')
            logger.info("configuring file: ${configFile}")
            configFile.setText(esConfig.collect { key, value -> "${key}: ${value}" }.join('\n'), 'UTF-8')
        }
    }

    static Task configureJvmOptions(String name, Project project, Task setup, NodeInfo node) {
        Task writeJvmOptions = project.tasks.create(name: name, type: DefaultTask, dependsOn: setup)
        writeJvmOptions.doFirst {
            URL url = getClass().getResource('/jvm.options')
            if (url) {
                File jvmOptionsFile = new File(node.pathConf, 'jvm.options')
                jvmOptionsFile.setText(url.text, 'UTF-8')
                logger.info("configuring JVM: ${url.text}")
            } else {
                logger.info('no jvm.options found')
            }
        }
    }

    /** Adds a task to create keystore */
    static Task configureCreateKeystoreTask(String name, Project project, Task setup, NodeInfo node) {
        if (node.config.keystoreSettings.isEmpty()) {
            return setup
        } else {
            Object esKeystoreUtil = "${-> node.binPath().resolve('elasticsearch-keystore').toString()}"
            return configureExecTask(name, project, setup, node, esKeystoreUtil, 'create')
        }
    }

    /** Adds tasks to add settings to the keystore */
    static Task configureAddKeystoreSettingTasks(String parent, Project project, Task setup, NodeInfo node) {
        Map kvs = node.config.keystoreSettings
        Task parentTask = setup
        Object esKeystoreUtil = "${-> node.binPath().resolve('elasticsearch-keystore').toString()}"
        for (Map.Entry<String, String> entry in kvs) {
            String key = entry.getKey()
            String name = taskName(parent, node, 'addToKeystore#' + key)
            Task t = configureExecTask(name, project, parentTask, node, esKeystoreUtil, 'add', key, '-x')
            String settingsValue = entry.getValue() // eval this early otherwise it will not use the right value
            t.doFirst {
                standardInput = new ByteArrayInputStream(settingsValue.getBytes(StandardCharsets.UTF_8))
            }
            parentTask = t
        }
        parentTask
    }

    static Task configureExtraConfigFilesTask(String name, Project project, Task setup, NodeInfo node) {
        if (node.config.extraConfigFiles.isEmpty()) {
            return setup
        }
        Copy copyConfig = project.tasks.create(name: name, type: Copy, dependsOn: setup) as Copy
        File configDir = new File(node.homeDir, 'config')
        copyConfig.into(configDir) // copy must always have a general dest dir, even though we don't use it
        for (Map.Entry<String,Object> extraConfigFile : node.config.extraConfigFiles.entrySet()) {
            Object extraConfigFileValue = extraConfigFile.getValue()
            copyConfig.doFirst {
                // make sure the copy won't be a no-op or act on a directory
                File srcConfigFile = project.file(extraConfigFileValue)
                if (srcConfigFile.isDirectory()) {
                    throw new GradleException("source for extraConfigFile must be a file: ${srcConfigFile}")
                }
                if (!srcConfigFile.exists()) {
                    throw new GradleException("source file for extraConfigFile does not exist: ${srcConfigFile}")
                }
            }
            File destConfigFile = new File(node.homeDir, 'config/' + extraConfigFile.getKey())
            // wrap source file in closure to delay resolution to execution time
            copyConfig.from({ extraConfigFileValue }) {
                // this must be in a closure so it is only applied to the single file specified in from above
                into(configDir.toPath().relativize(destConfigFile.canonicalFile.parentFile.toPath()).toFile())
                rename { destConfigFile.name }
            }
        }
        copyConfig
    }

    /**
     * Adds a task to copy plugins to a temp dir, which they will later be installed from.
     *
     * For each plugin, if the plugin has rest spec apis in its tests, those api files are also copied
     * to the test resources for this project.
     */
    static Task configureCopyPluginsTask(String name, Project project, Task setup, NodeInfo node, String prefix) {
        Copy copyPlugins = project.tasks.create(name: name, type: Copy, dependsOn: setup) as Copy

        List<FileCollection> pluginFiles = []
        for (Map.Entry<String, Project> plugin : node.config.plugins.entrySet()) {

            Project pluginProject = plugin.getValue()
            verifyProjectHasBuildPlugin(name, node.nodeVersion, project, pluginProject)
            String configurationName = pluginConfigurationName(prefix, pluginProject)
            Configuration configuration = project.configurations.findByName(configurationName)
            if (configuration == null) {
                configuration = project.configurations.create(configurationName)
            }
            project.dependencies.add(configurationName, project.dependencies.project(path: pluginProject.path, configuration: 'zip'))
            setup.dependsOn(pluginProject.tasks.bundlePlugin)

            // also allow rest tests to use the rest spec from the plugin
            String copyRestSpecTaskName = pluginTaskName('copy', plugin.getKey(), 'PluginRestSpec')
            Copy copyRestSpec = project.tasks.findByName(copyRestSpecTaskName) as Copy
            for (File resourceDir : pluginProject.sourceSets.test.resources.srcDirs) {
                File restApiDir = new File(resourceDir, 'rest-api-spec/api')
                if (!restApiDir.exists()) continue
                if (copyRestSpec == null) {
                    copyRestSpec = project.tasks.create(name: copyRestSpecTaskName, type: Copy) as Copy
                    copyPlugins.dependsOn(copyRestSpec)
                    copyRestSpec.into(project.sourceSets.test.output.resourcesDir)
                }
                copyRestSpec.from(resourceDir).include('rest-api-spec/api/**')
            }
            pluginFiles.add(configuration)
        }

        copyPlugins.into(node.pluginsTmpDir)
        copyPlugins.from(pluginFiles)
        copyPlugins
    }

    private static String pluginConfigurationName(String prefix, Project project) {
        "_plugin_${prefix}_${project.path}".replace(':', '_')
    }

    private static String pluginBwcConfigurationName(String prefix, Project project) {
        "_plugin_bwc_${prefix}_${project.path}".replace(':', '_')
    }

    /** Configures task to copy a plugin based on a zip file resolved using dependencies for an older version */
    static Task configureCopyBwcPluginsTask(String name, Project project, Task setup, NodeInfo node, String prefix) {
        Configuration bwcPlugins = project.configurations.getByName("${prefix}_elasticsearchBwcPlugins")
        for (Map.Entry<String, Project> plugin : node.config.plugins.entrySet()) {
            Project pluginProject = plugin.getValue()
            verifyProjectHasBuildPlugin(name, node.nodeVersion, project, pluginProject)
            String configurationName = pluginBwcConfigurationName(prefix, pluginProject)
            Configuration configuration = project.configurations.findByName(configurationName)
            if (configuration == null) {
                configuration = project.configurations.create(configurationName)
            }
            String depName = findPluginName(pluginProject)
            Dependency dep = bwcPlugins.dependencies.find {
                it.name == depName
            }
            configuration.dependencies.add(dep)
        }

        Copy copyPlugins = project.tasks.create(name: name, type: Copy, dependsOn: setup) {
            from bwcPlugins
            into node.pluginsTmpDir
        } as Copy
        copyPlugins
    }

    static Task configureInstallModuleTask(String name, Project project, Task setup, NodeInfo node, Project module) {
        if (!node.config.distribution.contains('integ-test')) {
            throw new GradleException("Module ${module.path} not allowed in distributions other than integ-test because they should already have all modules bundled")
        }
        if (!module.plugins.hasPlugin(PluginBuildPlugin)) {
            throw new GradleException("Task ${name} cannot include module ${module.path} which is not an esplugin")
        }
        Copy installModule = project.tasks.create(name, Copy.class)
        installModule.dependsOn(setup)
        installModule.dependsOn(module.tasks.bundlePlugin)
        installModule.into(new File(node.homeDir, "modules/${module.name}"))
        installModule.from({ project.zipTree(module.tasks.bundlePlugin.outputs.files.singleFile) })
        installModule
    }

    static Task configureInstallPluginTask(String name, Project project, Task setup, NodeInfo node, Project plugin, String prefix) {
        FileCollection pluginZip
        if (node.nodeVersion != VersionProperties.getVersion('elasticsearch')) {
            pluginZip = project.configurations.getByName(pluginBwcConfigurationName(prefix, plugin))
        } else {
            pluginZip = project.configurations.getByName(pluginConfigurationName(prefix, plugin))
        }
        Object file = "${-> new File(node.pluginsTmpDir, pluginZip.singleFile.getName()).toURI().toURL().toString()}"
        // removed, we do not have a 'elasticsearch-plugin' binary any more!
        /*
        Object esPluginUtil = "${-> node.binPath().resolve('elasticsearch-plugin').toString()}"
        Object[] args = [esPluginUtil, 'install', '--batch', file]
        configureExecTask(name, project, setup, node, args)
        */
        logger.info('skipped: plugin install per command line: ' + file)
        Copy installPlugin = project.tasks.create(name, Copy.class)
        installPlugin.dependsOn(setup)
        installPlugin.dependsOn(plugin.tasks.bundlePlugin)
        installPlugin.into(new File(node.homeDir, "plugins/${plugin.name}"))
        installPlugin.from({ project.zipTree(plugin.tasks.bundlePlugin.outputs.files.singleFile) })
        installPlugin
    }

    /** Wrapper for command line argument: surrounds comma with double quotes **/
    private static class EscapeCommaWrapper {

        Object arg

        @Override
        String toString() {
            String s = arg.toString()

            /// Surround strings that contains a comma with double quotes
            if (s.indexOf(',') != -1) {
                return "\"${s}\""
            }
            s
        }
    }

    /** Adds a task to execute a command to help setup the cluster */
    static Task configureExecTask(String name, Project project, Task setup, NodeInfo node, Object[] execArgs) {
        project.tasks.create(name: name, type: LoggedExec, dependsOn: setup) {
            workingDir node.cwd
            commandLine execArgs
        }
    }

    /** Adds a task to start an elasticsearch node with the given configuration */
    static Task configureStartTask(String name, Project project, Task setup, NodeInfo node) {

        // this closure is converted into ant nodes by groovy's AntBuilder
        Closure antRunner = { AntBuilder ant ->
            ant.exec(executable: node.executable, spawn: node.config.daemonize, dir: node.cwd, taskname: 'elasticsearch') {
                node.env.each { key, value -> env(key: key, value: value) }
                node.args.each { arg(value: it) }
            }
        }

        // this closure is the actual code to run elasticsearch
        Closure elasticsearchRunner = {
            // Due to how ant exec works with the spawn option, we lose all stdout/stderr from the
            // process executed. To work around this, when spawning, we wrap the elasticsearch start
            // command inside another shell script, which simply internally redirects the output
            // of the real elasticsearch script. This allows ant to keep the streams open with the
            // dummy process, but us to have the output available if there is an error in the
            // elasticsearch start script
            if (node.config.daemonize) {
                node.writeWrapperScript()
            }

            // we must add debug options inside the closure so the config is read at execution time, as
            // gradle task options are not processed until the end of the configuration phase
            if (node.config.debug) {
                println 'Running elasticsearch in debug mode, suspending until connected on port 8000'
                node.env['ES_JAVA_OPTS'] = '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000'
            }

            node.getCommandString().eachLine { line -> logger.info(line) }

            if (logger.isInfoEnabled() || !node.config.daemonize) {
                runAntCommand(project, antRunner, System.out, System.err)
            } else {
                // buffer the output, we may not need to print it
                PrintStream captureStream = new PrintStream(node.buffer, true, "UTF-8")
                runAntCommand(project, antRunner, captureStream, captureStream)
            }
        }

        Task start = project.tasks.create(name: name, type: DefaultTask, dependsOn: setup)
        start.doLast(elasticsearchRunner)
        start
    }

    static Task configureWaitTask(String name, Project project, List<NodeInfo> nodes, List<Task> startTasks, int waitSeconds) {
        Task wait = project.tasks.create(name: name, dependsOn: startTasks)
        wait.doLast {
            ant.waitfor(maxwait: "${waitSeconds}", maxwaitunit: 'second', checkevery: '500', checkeveryunit: 'millisecond', timeoutproperty: "failed${name}") {
                or {
                    for (NodeInfo node : nodes) {
                        resourceexists {
                            file(file: node.failedMarker.toString())
                        }
                    }
                    and {
                        for (NodeInfo node : nodes) {
                            resourceexists {
                                file(file: node.pidFile.toString())
                            }
                            resourceexists {
                                file(file: node.httpPortsFile.toString())
                            }
                            resourceexists {
                                file(file: node.transportPortsFile.toString())
                            }
                        }
                    }
                }
            }
            boolean anyNodeFailed = false
            for (NodeInfo node : nodes) {
                anyNodeFailed |= node.failedMarker.exists()
            }
            if (ant.properties.containsKey("failed${name}".toString()) || anyNodeFailed) {
                waitFailed(project, nodes, logger, 'Failed to start elasticsearch')
            }

            // make sure all files exist otherwise we haven't fully started up
            boolean missingFile = false
            for (NodeInfo node : nodes) {
                missingFile |= !node.pidFile.exists()
                missingFile |= !node.httpPortsFile.exists()
                missingFile |= !node.transportPortsFile.exists()
            }
            if (missingFile) {
                waitFailed(project, nodes, logger, 'Elasticsearch did not complete startup in time allotted')
            }

            // go through each node checking the wait condition
            for (NodeInfo node : nodes) {
                // first bind node info to the closure, then pass to the ant runner so we can get good logging
                Closure antRunner = node.config.waitCondition.curry(node)

                boolean success
                if (logger.isInfoEnabled()) {
                    success = runAntCommand(project, antRunner, System.out, System.err)
                } else {
                    PrintStream captureStream = new PrintStream(node.buffer, true, "UTF-8")
                    success = runAntCommand(project, antRunner, captureStream, captureStream)
                }

                if (!success) {
                    waitFailed(project, nodes, logger, 'Elasticsearch cluster failed to pass wait condition')
                }
            }
        }
        return wait
    }

    static void waitFailed(Project project, List<NodeInfo> nodes, Logger logger, String msg) {
        for (NodeInfo node : nodes) {
            if (!logger.isInfoEnabled()) {
                // We already log the command at info level. No need to do it twice.
                node.getCommandString().eachLine { line -> logger.error(line) }
            }
            logger.error("Node ${node.nodeNum} output:")
            logger.error("|-----------------------------------------")
            logger.error("|  failure marker exists: ${node.failedMarker.exists()}")
            logger.error("|  pid file exists: ${node.pidFile.exists()}")
            logger.error("|  http ports file exists: ${node.httpPortsFile.exists()}")
            logger.error("|  transport ports file exists: ${node.transportPortsFile.exists()}")
            // the waitfor failed, so dump any output we got (if info logging this goes directly to stdout)
            logger.error("|\n|  [ant output]")
            node.buffer.toString('UTF-8').eachLine { line -> logger.error("|    ${line}") }
            // also dump the log file for the startup script (which will include ES logging output to stdout)
            if (node.startLog.exists()) {
                logger.error("|\n|  [log]")
                node.startLog.eachLine { line -> logger.error("|    ${line}") }
            }
            if (node.pidFile.exists() && !node.failedMarker.exists() &&
                (!node.httpPortsFile.exists() || !node.transportPortsFile.exists())) {
                logger.error("|\n|  [jstack]")
                String pid = node.pidFile.getText('UTF-8')
                ByteArrayOutputStream output = new ByteArrayOutputStream()
                project.exec {
                    commandLine = ["jstack", pid]
                    standardOutput = output
                }
                output.toString('UTF-8').eachLine { line -> logger.error("|    ${line}") }
            }
            logger.error("|-----------------------------------------")
        }
        throw new GradleException(msg)
    }

    /** Adds a task to check if the process with the given pidfile is actually elasticsearch */
    static Task configureCheckPreviousTask(String name, Project project, Object depends, NodeInfo node) {
        project.tasks.create(name: name, type: Exec, dependsOn: depends) {
            onlyIf { node.pidFile.exists() }
            // the pid file won't actually be read until execution time, since the read is wrapped within an inner closure of the GString
            ext.pid = "${ -> node.pidFile.getText('UTF-8').trim()}"
            commandLine 'jps', '-l'
            standardOutput = new ByteArrayOutputStream()
            doLast {
                String out = standardOutput.toString()
                if (!out.contains("${ext.pid} org.elasticsearch.bootstrap.Elasticsearch")) {
                    logger.error('jps -l')
                    logger.error(out)
                    logger.error("pid file: ${node.pidFile}")
                    logger.error("pid: ${ext.pid}")
                    throw new GradleException("jps -l did not report any process with org.elasticsearch.bootstrap.Elasticsearch\n" +
                            "Did you run gradle clean? Maybe an old pid file is still lying around.")
                } else {
                    logger.info(out)
                }
            }
        }
    }

    /** Adds a task to kill an elasticsearch node with the given pidfile */
    static Task configureStopTask(String name, Project project, Object depends, NodeInfo node) {
        project.tasks.create(name: name, type: LoggedExec, dependsOn: depends) {
            onlyIf { node.pidFile.exists() }
            // the pid file won't actually be read until execution time, since the read is wrapped within an inner closure of the GString
            ext.pid = "${ -> node.pidFile.getText('UTF-8').trim()}"
            doFirst {
                logger.info("Shutting down external node with pid ${pid}")
            }
            executable 'kill'
            args '-9', pid
            doLast {
                project.delete(node.pidFile)
            }
        }
    }

    /** Returns a unique task name for this task and node configuration */
    static String taskName(String prefix, NodeInfo node, String action) {
        if (node.config.numNodes > 1) {
            return "${prefix}#node${node.nodeNum}.${action}"
        } else {
            return "${prefix}#${action}"
        }
    }

    static String pluginTaskName(String action, String name, String suffix) {
        // replace every dash followed by a character with just the uppercase character
        String camelName = name.replaceAll(/-(\w)/) { _, c -> c.toUpperCase(Locale.ROOT) }
        action + camelName[0].toUpperCase(Locale.ROOT) + camelName.substring(1) + suffix
    }

    /** Runs an ant command, sending output to the given out and error streams */
    static Object runAntCommand(Project project, Closure command, PrintStream outputStream, PrintStream errorStream) {
        DefaultLogger listener = new DefaultLogger(
                errorPrintStream: errorStream,
                outputPrintStream: outputStream,
                messageOutputLevel: org.apache.tools.ant.Project.MSG_INFO)

        project.ant.project.addBuildListener(listener)
        Object retVal = command(project.ant)
        project.ant.project.removeBuildListener(listener)
        retVal
    }

    static void verifyProjectHasBuildPlugin(String name, String version, Project project, Project pluginProject) {
        if (!pluginProject.plugins.hasPlugin(PluginBuildPlugin) && !pluginProject.plugins.hasPlugin(MetaPluginBuildPlugin)) {
            throw new GradleException("Task [${name}] cannot add plugin [${pluginProject.path}] with version [${version}] to project's " +
                    "[${project.path}] dependencies: the plugin is not an esplugin or es_meta_plugin")
        }
    }

    /** Find the plugin name in the given project, whether a regular plugin or meta plugin. */
    static String findPluginName(Project pluginProject) {
        PluginPropertiesExtension extension =
                pluginProject.extensions.findByName('esplugin') as PluginPropertiesExtension
        extension != null ? extension.name : pluginProject.extensions.findByName('es_meta_plugin').name
    }
}
