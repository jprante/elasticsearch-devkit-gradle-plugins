package org.xbib.gradle.task.elasticsearch.qa

import de.thetaphi.forbiddenapis.gradle.ForbiddenApisPlugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin

/**
 * Validation tasks which should be run before committing. These run before tests.
 */
class QualityAssuranceTasks {

    /**
     * Adds a quality assurance task, which depends on non-test verification tasks.
     */
    static Task create(Project project, boolean includeDependencyLicenses) {
        List<Task> precommitTasks = [
            configureForbiddenApis(project),
            configureCheckstyle(project),
            configureNamingConventions(project),
            project.tasks.create('forbiddenPatterns', ForbiddenPatternsTask),
            project.tasks.create('licenseHeaders', LicenseHeadersTask),
            project.tasks.create('jarHell', JarHellTask),
            project.tasks.create('thirdPartyAudit', ThirdPartyAuditTask)]

        // tasks with just tests don't need dependency licenses, so this flag makes adding
        // the task optional
        if (includeDependencyLicenses) {
            DependencyLicensesTask dependencyLicenses = project.tasks.create('dependencyLicenses', DependencyLicensesTask.class)
            precommitTasks.add(dependencyLicenses)
            // we also create the updateShas helper task that is associated with dependencyLicenses
            UpdateShasTask updateShas = project.tasks.create('updateShas', UpdateShasTask.class)
            updateShas.parentTask = dependencyLicenses
        }
        if (project.path != ':gradle-plugin-elasticsearch-build' &&
            project.path != ':elasticsearch-test-loggerusage') {
            /*
             * Sadly, dev kit can't have logger-usage-check because that
             * would create a circular project dependency between devkit
             * (which provides NamingConventionsCheck) and :elasticsearch-test-loggerusage
             * which provides the logger usage check. Since the dev kit
             * don't use the logger usage check because they don't have any
             * of Elasticsearch's loggers and :elasticsearch-test-loggerusage actually does
             * use the NamingConventionsCheck we break the circular dependency
             * here.
             */
            precommitTasks.add(configureLoggerUsage(project))
        }

        Map<String, Object> precommitOptions = [
            name: 'precommit',
            group: JavaBasePlugin.VERIFICATION_GROUP,
            description: 'Runs all non-test checks.',
            dependsOn: precommitTasks
        ]
        return project.tasks.create(precommitOptions)
    }

    private static Task configureForbiddenApis(Project project) {
        project.pluginManager.apply(ForbiddenApisPlugin)
        project.forbiddenApis {
            failOnUnsupportedJava = false
            bundledSignatures = ['jdk-unsafe', 'jdk-deprecated', 'jdk-non-portable', 'jdk-system-out']
            signaturesURLs = [getClass().getResource('/forbidden/jdk-signatures.txt'),
                              getClass().getResource('/forbidden/es-all-signatures.txt')]
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
        forbiddenApis.group = "" // clear group, so this does not show up under verification tasks
        return forbiddenApis
    }

    private static Task configureCheckstyle(Project project) {
        // Always copy the checkstyle configuration files to 'buildDir/checkstyle'
        // since the resources could be located in a jar file.
        // If the resources are located in a jar, Gradle will fail when it tries to turn the URL into a file
        URL checkstyleConfUrl = QualityAssuranceTasks.getResource("/checkstyle.xml")
        URL checkstyleSuppressionsUrl = QualityAssuranceTasks.getResource("/checkstyle_suppressions.xml")
        File checkstyleDir = new File(project.buildDir, "checkstyle")
        File checkstyleSuppressions = new File(checkstyleDir, "checkstyle_suppressions.xml")
        File checkstyleConf = new File(checkstyleDir, "checkstyle.xml");
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
        project.checkstyle {
            config = project.resources.text.fromFile(checkstyleConf, 'UTF-8')
            configProperties = [
                suppressions: checkstyleSuppressions
            ]
            toolVersion = 7.5
        }
        for (String taskName : ['checkstyleMain', 'checkstyleTest']) {
            Task task = project.tasks.findByName(taskName)
            if (task != null) {
                project.tasks['check'].dependsOn.remove(task)
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

    private static Task configureNamingConventions(Project project) {
        if (project.sourceSets.findByName("test")) {
            return project.tasks.create('namingConventions', NamingConventionsTask)
        }
        return null
    }

    private static Task configureLoggerUsage(Project project) {
        Task loggerUsageTask = project.tasks.create('loggerUsageCheck', LoggerUsageTask)
        project.configurations.create('loggerUsagePlugin')
        project.dependencies.add('loggerUsagePlugin',
                "org.xbib.elasticsearch:elasticsearch-test-loggerusage:${project.property('xbib-elasticsearch-test.version')}")
        loggerUsageTask.configure {
            classpath = project.configurations.loggerUsagePlugin
        }
        return loggerUsageTask
    }
}
