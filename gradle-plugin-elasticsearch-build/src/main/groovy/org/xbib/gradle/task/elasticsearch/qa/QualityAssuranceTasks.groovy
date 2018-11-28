package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.xbib.gradle.plugin.forbiddenapis.ForbiddenApisPlugin

/**
 * Quality assurance tasks which should be run before committing.
 */
class QualityAssuranceTasks {

    /**
     * Adds a quality assurance task, which depends on non-test verification tasks.
     */
    Task create(Project project, boolean includeDependencyLicenses) {
        List<Task> qualityTasks = [
            configureForbiddenApis(project),
            configureNamingConventions(project),
            configureCheckstyle(project),
            configureForbiddenPatterns(project),
            configureLicenseHeaders(project),
            configureJarHell(project),
            configureThirdPartyAudit(project)
        ]

        if (includeDependencyLicenses) {
            DependencyLicensesTask dependencyLicenses = project.tasks.create('dependencyLicenses', DependencyLicensesTask.class)
            qualityTasks.add(dependencyLicenses)
            UpdateShasTask updateShas = project.tasks.create('updateShas', UpdateShasTask.class)
            updateShas.parentTask = dependencyLicenses
        }
        qualityTasks.add(configureLoggerUsage(project))

        Map<String, Object> precommitOptions = [
            name: 'precommit',
            group: 'verification',
            description: 'Runs all quality tasks.',
            dependsOn: qualityTasks
        ]
        project.tasks.create(precommitOptions)
    }

    private Task configureForbiddenApis(Project project) {
        project.pluginManager.apply(ForbiddenApisPlugin)
        project.forbiddenApis {
            ignoreFailures = true
            bundledSignatures = ['jdk-unsafe', 'jdk-deprecated', 'jdk-non-portable', 'jdk-system-out']
            String jdkSignatures = JavaVersion.current().java11Compatible ?
                    'jdk-11-signatures.txt' : 'jdk-signatures.txt'
            signaturesURLs = [
                    getClass().getResource('/forbidden/' + jdkSignatures),
                    getClass().getResource('/forbidden/es-all-signatures.txt')
            ]
            suppressAnnotations = ['**.SuppressForbidden']
        }
        Task mainForbidden = project.tasks.findByName('forbiddenApisMain')
        if (mainForbidden != null) {
            mainForbidden.configure {
                signaturesURLs += getClass().getResource('/forbidden/es-server-signatures.txt')
            }
        }
        Task testForbidden = project.tasks.findByName('forbiddenApisTest')
        if (testForbidden != null) {
            testForbidden.configure {
                signaturesURLs += getClass().getResource('/forbidden/es-test-signatures.txt')
                signaturesURLs += getClass().getResource('/forbidden/http-signatures.txt')
            }
        }
        Task forbiddenApis = project.tasks.findByName('forbiddenApis')
        forbiddenApis
    }

    private Task configureCheckstyle(Project project) {
        if (!project.hasProperty('checkstyle.version')) {
            throw new GradleException('project property checkstyle.version not set')
        }
        // Always copy the checkstyle configuration files to 'buildDir/checkstyle'
        // since the resources could be located in a jar file.
        // If the resources are located in a jar, Gradle will fail when it tries to turn the URL into a file
        URL checkstyleConfUrl = QualityAssuranceTasks.getResource("/checkstyle.xml")
        URL checkstyleSuppressionsUrl = QualityAssuranceTasks.getResource("/checkstyle_suppressions.xml")
        File checkstyleDir = new File(project.buildDir, "checkstyle")
        File checkstyleSuppressions = new File(checkstyleDir, "checkstyle_suppressions.xml")
        File checkstyleConf = new File(checkstyleDir, "checkstyle.xml")
        Task copyCheckstyleConf = project.tasks.create("copyCheckstyleConf")
        // configure inputs and outputs so up to date works properly
        copyCheckstyleConf.outputs.files(checkstyleSuppressions, checkstyleConf)
        if ("jar".equals(checkstyleConfUrl.getProtocol())) {
            JarURLConnection jarURLConnection = (JarURLConnection) checkstyleConfUrl.openConnection()
            copyCheckstyleConf.inputs.file(jarURLConnection.getJarFileURL())
        } else if ("file".equals(checkstyleConfUrl.getProtocol())) {
            copyCheckstyleConf.inputs.files(checkstyleConfUrl.getFile(), checkstyleSuppressionsUrl.getFile())
        }
        copyCheckstyleConf.doLast {
            checkstyleDir.mkdirs()
            // withStream will close the output stream and IOGroovyMethods#getBytes reads the InputStream fully and closes it
            new FileOutputStream(checkstyleConf).withStream {
                it.write(checkstyleConfUrl.openStream().getBytes())
            }
            new FileOutputStream(checkstyleSuppressions).withStream {
                it.write(checkstyleSuppressionsUrl.openStream().getBytes())
            }
        }
        Task checkstyleTask = project.tasks.create('checkstyle')
        // Apply the checkstyle plugin to create `checkstyleMain` and `checkstyleTest`. It only
        // creates them if there is main or test code to check and it makes `check` depend
        // on them. But we want `precommit` to depend on `checkstyle` which depends on them so
        // we have to swap them.
        project.pluginManager.apply('checkstyle')
        String checkstyleVersion = project.hasProperty('checkstyle.version') ?
                project.property('checkstyle.version') : '8.13'
        project.checkstyle {
            config = project.resources.text.fromFile(checkstyleConf, 'UTF-8')
            configProperties = [
                suppressions: checkstyleSuppressions
            ]
            toolVersion = checkstyleVersion
        }
        project.checkstyleMain {
            exclude '**/module-info.java'
        }
        project.checkstyleTest {
            exclude '**/module-info.java'
        }
        for (String taskName : ['checkstyleMain', 'checkstyleTest']) {
            Task task = project.tasks.findByName(taskName)
            if (task != null) {
                //project.tasks['check'].dependsOn.remove(task)
                checkstyleTask.dependsOn(task)
                task.dependsOn(copyCheckstyleConf)
                task.inputs.file(checkstyleSuppressions)
                task.reports {
                    html.enabled false
                }
            }
        }
        checkstyleTask
    }

    /*private Task configureOldLoggerUsage(Project project) {
        if (!project.hasProperty('elasticsearch-devkit.version')) {
            throw new GradleException('property elasticsearch-devkit.version not set')
        }
        Task loggerUsageTask = project.tasks.create('loggerUsageCheck', LoggerUsageTask)
        project.configurations.create('loggerUsagePlugin')
        project.dependencies.add('loggerUsagePlugin',
                "org.xbib.elasticsearch:elasticsearch-test-loggerusage:${project.property('elasticsearch-devkit.version')}")
        loggerUsageTask.configure {
            classpath = project.configurations.loggerUsagePlugin
        }
        loggerUsageTask
    }*/

    private Task configureLoggerUsage(Project project) {
        project.tasks.create('loggerUsageCheck', LoggerUsageTask)
    }

    private Task configureNamingConventions(Project project) {
        project.sourceSets.findByName("test") ? project.tasks.create('namingConventions', NamingConventionsTask) : null
    }

    private Task configureForbiddenPatterns(Project project) {
        project.tasks.create('forbiddenPatterns', ForbiddenPatternsTask)
    }

    private Task configureLicenseHeaders(Project project) {
        project.tasks.create('licenseHeaders', LicenseHeadersTask)
    }

    private Task configureJarHell(Project project) {
        project.tasks.create('jarHell', JarHellTask)
    }

    private Task configureThirdPartyAudit(Project project) {
        project.tasks.create('thirdPartyAudit', ThirdPartyAuditTask)
    }

}
