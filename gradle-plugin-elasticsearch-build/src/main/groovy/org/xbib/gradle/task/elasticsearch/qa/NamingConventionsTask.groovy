package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile

/**
 * Runs NamingConventionsCheck on a classpath/directory combo to verify that
 * tests are named according to our conventions so they'll be picked up by
 * gradle. Read the Javadoc for NamingConventionsCheck to learn more.
 */
class NamingConventionsTask extends LoggedResultJavaExec {
    /**
     * We use a simple "marker" file that we touch when the task succeeds
     * as the task output. This is compared against the modified time of the
     * inputs (ie the jars/class files).
     */
    @OutputFile
    File successMarker = new File(project.buildDir, "markers/${this.name}")

    /**
     * Should we skip the integ tests in disguise tests? Defaults to true because only core names its
     * integ tests correctly.
     */
    @Input
    boolean skipIntegTestInDisguise = false

    /**
     * Superclass for all tests.
     */
    @Input
    String testClass = 'org.apache.lucene.util.LuceneTestCase'

    /**
     * Superclass for all integration tests.
     */
    @Input
    String integTestClass = 'org.elasticsearch.test.ESIntegTestCase'

    /**
     * Should the test also check the main classpath for test classes instead of
     * doing the usual checks to the test classpath.
     */
    @Input
    boolean checkForTestsInMain = false;

    NamingConventionsTask() {
        // Extra classpath contains the actual test
        if (!project.configurations.names.contains('namingConventions')) {
            project.configurations.create('namingConventions')
            Dependency buildToolsDep = project.dependencies.add('namingConventions',
                    "org.xbib.elasticsearch:gradle-plugin-elasticsearch-build:${project.version}")
            buildToolsDep.transitive = false // We don't need gradle in the classpath. It conflicts.
        }
        FileCollection extraClasspath = project.configurations.namingConventions
        dependsOn(extraClasspath)

        FileCollection runtimeClasspath = project.sourceSets.test.runtimeClasspath
        inputs.files(runtimeClasspath)
        description = "Tests that test classes aren't misnamed or misplaced"
        //executable = new File(project.runtimeJavaHome, 'bin/java')
        if (!checkForTestsInMain) {
            /* This task is created by default for all subprojects with this
             * setting and there is no point in running it if the files don't
             * exist. */
            FileCollection fileCollection = project.sourceSets.test.output.classesDirs
            List<File> fileExists = fileCollection.findAll() { it.exists() }
            onlyIf { !fileExists.isEmpty() }
        }

        /*
         * We build the arguments in a funny afterEvaluate/doFirst closure so that we can wait for the classpath to be
         * ready for us. Strangely neither one on their own are good enough.
         */
        project.afterEvaluate {
            doFirst {
                classpath runtimeClasspath + extraClasspath
                main = 'org.xbib.elasticsearch.test.NamingConventionsCheck'
                jvmArgs('-Djna.nosys=true')
                //args('-cp', (classpath + extraClasspath).asPath, 'org.xbib.elasticsearch.test.NamingConventionsCheck')
                args('--test-class', testClass)
                if (skipIntegTestInDisguise) {
                    args('--skip-integ-tests-in-disguise')
                } else {
                    args('--integ-test-class', integTestClass)
                }
                /*
                 * The test framework has classes that fail the checks to validate that the checks fail properly.
                 * Since these would cause the build to fail we have to ignore them with this parameter. The
                 * process of ignoring them lets us validate that they were found so this ignore parameter acts
                 * as the test for the NamingConventionsCheck.
                 */
                if (':gradle-plugin-elasticsearch-build'.equals(project.path)) {
                    args('--self-test')
                }
                if (checkForTestsInMain) {
                    args('--main')
                    args('--')
                    FileCollection fileCollection = project.sourceSets.main.output.classesDirs
                    args(fileCollection.first().absolutePath)
                } else {
                    args('--')
                    FileCollection fileCollection = project.sourceSets.test.output.classesDirs
                    args(fileCollection.first().absolutePath)
                }
            }
        }
        doLast { successMarker.setText("", 'UTF-8') }
    }
}
