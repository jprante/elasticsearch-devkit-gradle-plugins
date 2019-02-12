package org.xbib.gradle.plugin.elasticsearch.build

import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.xbib.gradle.plugin.elasticsearch.VersionCollection
import org.xbib.gradle.plugin.elasticsearch.VersionProperties
import org.xbib.gradle.plugin.randomizedtesting.RandomizedTestingTask
import org.xbib.gradle.task.elasticsearch.build.DependenciesInfoTask
import org.xbib.gradle.task.elasticsearch.qa.QualityAssuranceTasks

import java.lang.module.ModuleDescriptor
import java.lang.module.ModuleFinder
import java.lang.module.ModuleReference
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Encapsulates build configuration for Elasticsearch projects.
 */
class BuildPlugin implements Plugin<Project> {

    private static final Logger logger = LogManager.getLogger(BuildPlugin)

    static final JavaVersion minimumRuntimeVersion = JavaVersion.VERSION_11

    static final JavaVersion minimumCompilerVersion = JavaVersion.VERSION_11

    @Override
    void apply(Project project) {
        if (project.pluginManager.hasPlugin('org.xbib.gradle.plugin.elasticsearch.standalone-rest-test')) {
              throw new InvalidUserDataException('org.xbib.gradle.plugin.elasticsearch.standalone-test, ' +
                      'org.xbib.gradle.plugin.elasticearch.standalone-rest-test, and org.xbib.gradle.plugin.elasticsearch.build ' +
                      'are mutually exclusive')
        }
        project.pluginManager.apply('java')
        project.pluginManager.apply('org.xbib.gradle.plugin.randomizedtesting')
        configureJars(project)
        createProvidedConfiguration(project)
        globalBuildInfo(project)
        configureRepositories(project)
        project.ext.versions = VersionProperties.getAllVersions()
        configureCompile(project)
        configureJavadoc(project)
        configureSourcesJar(project)
        configureTestJar(project)
        configureTask(project)
        configureQualityAssurance(project)
        configureDependenciesInfo(project)
    }

    /** Performs checks on the build environment and prints information about the build environment. */
    static void globalBuildInfo(Project project) {
        if (project.rootProject.ext.has('buildChecksDone') == false) {
            String javaVendor = System.getProperty('java.vendor')
            String javaVersion = System.getProperty('java.version')
            String gradleJavaVersionDetails = "${javaVendor} ${javaVersion}" +
                " [${System.getProperty('java.vm.name')} ${System.getProperty('java.vm.version')}]"
            String compilerJavaVersionDetails = gradleJavaVersionDetails
            JavaVersion compilerJavaVersionEnum = JavaVersion.current()
            String runtimeJavaVersionDetails = gradleJavaVersionDetails
            JavaVersion runtimeJavaVersionEnum = JavaVersion.current()

            print """
Welcome to xbib's Elasticsearch dev kit gradle build plugin. Meouw.

    (\\___/)
    (='.'=)

"""
            println "Date: ${ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)}"
            println "Host: ${InetAddress.getLocalHost()} OS: ${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})"
            if (gradleJavaVersionDetails != compilerJavaVersionDetails || gradleJavaVersionDetails != runtimeJavaVersionDetails) {
                print "Java (gradle): ${gradleJavaVersionDetails} Java (compile): ${compilerJavaVersionDetails} Java (runtime): ${runtimeJavaVersionDetails}"
            } else {
                print "Java: ${gradleJavaVersionDetails}"
            }
            println " Groovy: ${GroovySystem.version} Gradle: ${project.gradle.gradleVersion}"
            println "Random Testing Seed: ${project.testSeed}"

            if (compilerJavaVersionEnum < minimumCompilerVersion) {
                logger.warn("Java ${minimumCompilerVersion} or above is required to build Elasticsearch")
            }
            if (runtimeJavaVersionEnum < minimumRuntimeVersion) {
                logger.warn("Java ${minimumRuntimeVersion} or above is required to run Elasticsearch")
            }

            project.rootProject.ext.compilerJavaVersion = compilerJavaVersionEnum
            project.rootProject.ext.runtimeJavaVersion = runtimeJavaVersionEnum
            project.rootProject.ext.buildChecksDone = true

            project.rootProject.ext.elasticsearchVersionCollection = new VersionCollection(project)
        }

        project.targetCompatibility = minimumRuntimeVersion
        project.sourceCompatibility = minimumRuntimeVersion

        project.ext.compilerJavaVersion = project.rootProject.ext.compilerJavaVersion
        project.ext.runtimeJavaVersion = project.rootProject.ext.runtimeJavaVersion
    }

    /**
     * Adds repositories used by ES dependencies.
     */
    static void configureRepositories(Project project) {
        RepositoryHandler repos = project.repositories
        if (System.getProperty("repos.mavenlocal") != null) {
            // with -Drepos.mavenlocal=true we can force checking the local .m2 repo which is
            // useful for development ie. bwc tests where we install stuff in the local repository
            // such that we don't have to pass hardcoded files to gradle
            repos.mavenLocal()
        }
        repos.mavenCentral()
        String luceneVersion = VersionProperties.getVersion('lucene')
        if (luceneVersion != null && luceneVersion.contains('-snapshot')) {
            // extract the revision number from the version with a regex matcher
            String revision = (luceneVersion =~ /\w+-snapshot-([a-z0-9]+)/)[0][1]
            repos.maven {
                name 'lucene-snapshots'
                url "http://s3.amazonaws.com/download.elasticsearch.org/lucenesnapshots/${revision}"
            }
        }
    }

    /**
     * Adds compiler settings to the project.
     */
    static void configureCompile(Project project) {
        project.afterEvaluate {
            project.tasks.withType(JavaCompile) {
                final JavaVersion targetCompatibilityVersion = JavaVersion.toVersion(it.targetCompatibility)
                options.compilerArgs << '-Xlint:all,-deprecation,-serial'
                if (options.compilerArgs.contains("-processor") == false) {
                    options.compilerArgs << '-proc:none'
                }
                options.encoding = 'UTF-8'
                options.incremental = true
                if (JavaVersion.current().java9Compatible) {
                    options.compilerArgs << '--release' << targetCompatibilityVersion.majorVersion
                }
            }
            project.tasks.withType(GroovyCompile) {
                final JavaVersion targetCompatibilityVersion = JavaVersion.toVersion(it.targetCompatibility)
                if (JavaVersion.current().java9Compatible) {
                    options.compilerArgs << '--release' << targetCompatibilityVersion.majorVersion
                }
            }
        }
    }

    static void configureJavadoc(Project project) {
        configureJavadocJar(project)
    }

    /**
     * Adds a javadocJar task to generate a jar containing javadocs.
     */
    static void configureJavadocJar(Project project) {
        Task javadocJarTask = project.task('javadocJar', type: Jar)
        javadocJarTask.classifier = 'javadoc'
        javadocJarTask.group = 'build'
        javadocJarTask.description = 'Assembles a jar containing javadocs.'
        javadocJarTask.from(project.tasks.getByName(JavaPlugin.JAVADOC_TASK_NAME))
        project.assemble.dependsOn(javadocJarTask)
    }

    static void configureSourcesJar(Project project) {
        Task sourcesJarTask = project.task('sourcesJar', type: Jar)
        sourcesJarTask.classifier = 'sources'
        sourcesJarTask.group = 'build'
        sourcesJarTask.description = 'Assembles a jar containing source files.'
        sourcesJarTask.from(project.sourceSets.main.allSource)
        project.assemble.dependsOn(sourcesJarTask)
    }

    static void configureTestJar(Project project) {
        Task jarTask = project.tasks.findByName('jar')
        project.configurations.create('mains').setVisible(true).setTransitive(true)
        SourceSet mainSourceSet = project.sourceSets.findByName('main')
        if (mainSourceSet) {
            project.artifacts.add('mains', jarTask)
        }
        Task testJarTask = project.task('testJar', type: Jar)
        testJarTask.classifier = 'tests'
        testJarTask.group = 'build'
        testJarTask.description = 'Assembles a jar containing test classes.'
        testJarTask.from(project.sourceSets.test.output)
        testJarTask.dependsOn('testClasses')
        project.assemble.dependsOn(testJarTask)
        project.configurations.create('tests').setVisible(true).setTransitive(true)
        SourceSet testSourceSet = project.sourceSets.findByName('test')
        if (testSourceSet) {
            project.artifacts.add('tests', testJarTask)
        }
    }

    /**
     * Adds additional manifest info to jars.
     */
    static void configureJars(Project project) {
        project.ext.licenseFile = null
        project.ext.noticeFile = null
        project.tasks.withType(Jar) { Jar jarTask ->
            // we put all our distributable files under distributions
            jarTask.destinationDir = new File(project.buildDir, 'distributions')
            // fixup the jar manifest
            jarTask.doFirst {
                boolean isSnapshot = VersionProperties.getVersion('elasticsearch').endsWith("-SNAPSHOT")
                String version = VersionProperties.getVersion('elasticsearch')
                if (isSnapshot) {
                    version = version.substring(0, version.length() - 9)
                }
                // this doFirst is added before the info plugin, therefore it will run
                // after the doFirst added by the info plugin, and we can override attributes
                jarTask.manifest.attributes(
                        'X-Compile-Elasticsearch-Version': version,
                        'X-Compile-Lucene-Version': VersionProperties.getVersion('lucene'),
                        'X-Compile-Elasticsearch-Snapshot': isSnapshot,
                        'Build-Date': ZonedDateTime.now(ZoneOffset.UTC),
                        'Build-Java-Version': project.compilerJavaVersion)
            }
            // add license/notice files if they are present
            project.afterEvaluate {
                if (project.licenseFile) {
                    jarTask.into('META-INF') {
                        from(project.licenseFile.parent) {
                            include project.licenseFile.name
                        }
                    }
                }
                if (project.noticeFile) {
                    jarTask.into('META-INF') {
                        from(project.noticeFile.parent) {
                            include project.noticeFile.name
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns a closure of common configuration shared by unit and integration tests.
     */
    static Closure commonTestConfig(Project project) {
        return {
            //jvm "${project.runtimeJavaHome}/bin/java"
            parallelism System.getProperty('tests.jvms', 'auto')
            ifNoTests 'fail'
            onNonEmptyWorkDirectory 'wipe'
            leaveTemporary true
            jvmArg '-Xmx' + System.getProperty('tests.heap.size', '512m')
            jvmArg '-Xms' + System.getProperty('tests.heap.size', '512m')
            // Do we always want to auto-create heapdump dir?
            // Do we want to trash our file system with heap dumps by default? Not really.
            if (System.getProperty('tests.heapdump')) {
                jvmArg '-XX:+HeapDumpOnOutOfMemoryError'
                File heapdumpDir = new File(project.buildDir, 'heapdump')
                heapdumpDir.mkdirs()
                jvmArg '-XX:HeapDumpPath=' + heapdumpDir
            }
            if (project.runtimeJavaVersion >= JavaVersion.VERSION_1_9 && project.runtimeJavaVersion <= JavaVersion.VERSION_1_10) {
                jvmArg '--illegal-access=warn'
            }
            argLine System.getProperty('tests.jvm.argline')

            // we use './temp' since this is per JVM and tests are forbidden from writing to CWD
            systemProperty 'java.io.tmpdir', './temp'
            systemProperty 'java.awt.headless', 'true'
            systemProperty 'tests.gradle', 'true'
            systemProperty 'tests.artifact', project.name
            systemProperty 'tests.task', path
            systemProperty 'tests.security.manager', 'true'
            systemProperty 'jna.nosys', 'true'
            // default test sysprop values
            systemProperty 'tests.ifNoTests', 'fail'
            systemProperty 'tests.logger.level', System.getProperty('tests.logger.level', 'WARN')
            for (Map.Entry<Object, Object> property : System.properties.entrySet()) {
                String key = property.getKey().toString()
                if (key.startsWith('tests.') || key.startsWith('es.')) {
                    if (key.equals('tests.seed')) {
                        /* The seed is already set on the project so we
                         * shouldn't attempt to override it. */
                        continue
                    }
                    systemProperty property.getKey(), property.getValue()
                }
            }

            // Only set to 'true' because of randomizedtesting plugin.
            // Enabled assertions in test code are against fail-fast philosophy, they hide bugs in production mode
            // while they may expose bugs only in test mode.
            boolean assertionsEnabled = Boolean.parseBoolean(System.getProperty('tests.asserts', 'true'))
            enableSystemAssertions assertionsEnabled
            enableAssertions assertionsEnabled

            testLogging {
                showNumFailuresAtEnd 25
                slowTests {
                    heartbeat 10
                    summarySize 5
                }
                stackTraceFilters {
                    // custom filters: we carefully only omit test infra noise here
                    contains '.SlaveMain.'
                    regex(/^(\s+at )(org\.junit\.)/)
                    // also includes anonymous classes inside these two:
                    regex(/^(\s+at )(com\.carrotsearch\.randomizedtesting\.RandomizedRunner)/)
                    regex(/^(\s+at )(com\.carrotsearch\.randomizedtesting\.ThreadLeakControl)/)
                    regex(/^(\s+at )(com\.carrotsearch\.randomizedtesting\.rules\.)/)
                    regex(/^(\s+at )(org\.apache\.lucene\.util\.TestRule)/)
                    regex(/^(\s+at )(org\.apache\.lucene\.util\.AbstractBeforeAfterRule)/)
                }
                if (System.getProperty('tests.class') != null && System.getProperty('tests.output') == null) {
                    // if you are debugging, you want to see the output!
                    outputMode 'always'
                } else {
                    outputMode System.getProperty('tests.output', 'onerror')
                }
            }

            balancers {
                executionTime cacheFilename: ".local-${project.version}-${name}-execution-times.log"
            }

            listeners {
                junitXmlReport()
                junitHtmlReport()
            }
        }
    }

    /**
     * Configures the esTest task and let 'check' depends on it.
     * */
    static void configureTask(Project project) {
        Task testTask = project.tasks.findByPath('test')
        if (testTask == null) {
            // no test task, ok, user will use testing task on their own
            return
        }
        Task esTest = project.tasks.create([name: 'esTest',
                                            type: RandomizedTestingTask,
                                            dependsOn: ['jar', 'testJar'],
                                            group: JavaBasePlugin.VERIFICATION_GROUP,
                                            description: 'Runs Elasticsearch tests'
        ])
        esTest.configure(commonTestConfig(project))
        esTest.configure {
            doFirst {
                // set up java module graph from jar files
                project.files(project.configurations.mains.artifacts.files,
                        project.configurations.tests.artifacts.files).each { file ->
                    Set<ModuleReference> set = ModuleFinder.of(file.toPath()).findAll()
                    ModuleReference moduleReference = set.isEmpty() ? null : set.first()
                    ModuleDescriptor moduleDescriptor = moduleReference?.descriptor()
                    String moduleName = moduleDescriptor?.name()
                    if (moduleName) {
                        // add read access for secure mock
                        jvmArg "--add-reads=org.xbib.elasticsearch.securemock=${moduleName}"
                        logger.info("found module name for ${file}: ${moduleName}")
                    } else {
                        logger.warn("no module name for ${file}")
                    }
                }
            }
            jvmArg "--add-modules=ALL-MODULE-PATH"
            modulepath = project.files(project.configurations.testRuntime) +
                    project.configurations.mains.artifacts.files +
                    project.configurations.tests.artifacts.files
            testClassesDirs = testTask.testClassesDirs
            exclude '**/*$*.class'
            include '**/*Tests.class'
        }
        // Add a method to create additional unit tests for a project, which will share the same
        // randomized testing setup, but by default run no tests.
        project.extensions.add('additionalTest', { String name, Closure config ->
            RandomizedTestingTask additionalTest = project.tasks.create(name, RandomizedTestingTask)
            additionalTest.modulepath = esTest.modulepath
            additionalTest.testClassesDirs = esTest.testClassesDirs
            additionalTest.configure(commonTestConfig(project))
            additionalTest.configure(config)
            esTest.dependsOn(additionalTest)
        })
        Task checkTask = project.tasks.findByPath('check')
        checkTask.dependsOn.add(esTest)
    }

    private static Configuration createProvidedConfiguration(Project project) {
        project.configurations.create('provided')
                .setVisible(true)
                .setTransitive(true)
    }

    private static configureQualityAssurance(Project project) {
        QualityAssuranceTasks tasks = new QualityAssuranceTasks()
        Task quality = tasks.create(project, true)
        project.tasks.check.dependsOn(quality)
        // only require dependency licenses for non-elasticsearch deps
        project.dependencyLicenses.dependencies = project.configurations.runtime.fileCollection {
            (!it.group.startsWith('org.elasticsearch'))
        } - project.configurations.provided
    }

    private static configureDependenciesInfo(Project project) {
        Task deps = project.tasks.create("dependenciesInfo", DependenciesInfoTask.class)
        deps.dependencies = project.configurations.compile.allDependencies
    }

    /**
     * Return the configuration name used for finding transitive deps of the given dependency.
     * */
    private static String transitiveDepConfigName(String groupId, String artifactId, String version) {
        return "_transitive_${groupId}_${artifactId}_${version}"
    }
}
