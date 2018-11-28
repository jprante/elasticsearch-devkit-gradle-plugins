package org.xbib.gradle.plugin.forbiddenapis

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.resources.ResourceException
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.xbib.forbiddenapis.Checker
import org.xbib.forbiddenapis.Constants
import org.xbib.forbiddenapis.ForbiddenApiException
import org.xbib.forbiddenapis.ParseException

/**
 * <h3>ForbiddenApis Gradle Task</h3>
 * <p>
 * The plugin registers a separate task for each defined {@code sourceSet} using
 * the default task naming convention. For default Java projects, two tasks are created:
 * {@code forbiddenApisMain} and {@code forbiddenApisTest}. Additional source sets
 * will produce a task with similar names ({@code 'forbiddenApis' + nameOfSourceSet}).
 * All tasks are added as dependencies to the {@code check} default Gradle task.
 * For convenience, the plugin also defines an additional task {@code forbiddenApis}
 * that runs checks on all source sets.
 * <p>
 * Installation can be done from your {@code build.gradle} file using the Gradle {@code plugin} DSL:
 * <pre>
 * plugins {
 *  id 'de.thetaphi.forbiddenapis' version '@VERSION@'
 * }
 * </pre>
 * Alternatively, you can use the following script snippet if dynamic configuration is required (e.g., for own tasks):
 * <pre>
 * buildscript {
 *  repositories {
 *   mavenCentral()
 *  }
 *  dependencies {
 *   classpath '@GROUPID@:@ARTIFACTID@:@VERSION@'
 *  }
 * }
 *
 * apply plugin: 'de.thetaphi.forbiddenapis'
 * </pre>
 * After that you can add the following task configuration closures:
 * <pre>
 * forbiddenApisMain {
 *  bundledSignatures += 'jdk-system-out'
 * }
 * </pre>
 * <em>(using the {@code '+='} notation, you can add additional bundled signatures to the defaults).</em>
 * <p>
 * To define those defaults, which are used by all source sets, you can use:
 * <pre>
 * forbiddenApis {
 *  bundledSignatures = [ 'jdk-unsafe', 'jdk-deprecated' ]
 *  signaturesFiles = files('path/to/my/signatures.txt')
 *  ignoreFailures = false
 * }
 * </pre>
 *
 */
class ForbiddenApisTask extends DefaultTask implements PatternFilterable, VerificationTask, Constants {

    public static final FORBIDDENAPIS_TASK_NAME = 'forbiddenApis'

    private static final String NL = System.getProperty("line.separator", "\n")

    @Input
    PatternSet patternSet = new PatternSet().include("**/*.class")

    @OutputDirectories
    FileCollection classesDirs

    @InputFiles
    FileCollection classpath

    @Input
    @Optional
    String targetCompatibility

    @InputFiles
    @Optional
    FileCollection signaturesFiles

    @Input
    @Optional
    @Incubating
    Set<URL> signaturesURLs = new LinkedHashSet<URL>()

    @Input
    @Optional
    List<String> signatures = new ArrayList<String>()

    @Input
    @Optional
    Set<String> bundledSignatures = new LinkedHashSet<String>()

    @Input
    @Optional
    Set<String> suppressAnnotations = new LinkedHashSet<String>()

    @Deprecated
    @Input
    boolean internalRuntimeForbidden = false

    @Input
    boolean failOnUnsupportedJava = false

    @Input
    boolean failOnMissingClasses = true

    @Input
    boolean failOnUnresolvableSignatures = true

    @Input
    boolean ignoreFailures = false

    @Input
    boolean disableClassloadingCache = false

    /**
     * Set of patterns matching all class files to be parsed from the classesDirectory.
     * Can be changed to e.g. exclude several files (using excludes).
     * The default is a single include with pattern '**&#47;*.class'
     */
    @Input
    @Override
    Set<String> getIncludes() {
        patternSet.getIncludes()
    }

    @Override
    ForbiddenApisTask setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes)
        this
    }

    /**
     * Set of patterns matching class files to be excluded from checking.
     */
    @Override
    @Input
    Set<String> getExcludes() {
        patternSet.getExcludes()
    }

    @Override
    ForbiddenApisTask setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes)
        this
    }

    @Override
    ForbiddenApisTask exclude(String... arg0) {
        patternSet.exclude(arg0)
        this
    }

    @Override
    ForbiddenApisTask exclude(Iterable<String> arg0) {
        getPatternSet().exclude(arg0)
        this
    }

    @Override
    ForbiddenApisTask exclude(Spec<FileTreeElement> arg0) {
        patternSet.exclude(arg0)
        this
    }

    @Override
    ForbiddenApisTask exclude(@SuppressWarnings("rawtypes") Closure arg0) {
        patternSet.exclude(arg0)
        this
    }

    @Override
    ForbiddenApisTask include(String... arg0) {
        patternSet.include(arg0)
        this
    }

    @Override
    ForbiddenApisTask include(Iterable<String> arg0) {
        patternSet.include(arg0)
        this
    }

    @Override
    ForbiddenApisTask include(Spec<FileTreeElement> arg0) {
        patternSet.include(arg0)
        this
    }

    @Override
    ForbiddenApisTask include(@SuppressWarnings("rawtypes") Closure arg0) {
        patternSet.include(arg0)
        this
    }

    @InputFiles
    @SkipWhenEmpty
    FileTree getClassFiles() {
        classesDirs ? classesDirs.getAsFileTree().matching(getPatternSet()) : null
    }

    @TaskAction
    void checkForbidden() throws ForbiddenApiException {
        if (classesDirs == null || classpath == null) {
            // project without classes, ignore
            return
        }
        Set<File> cpElements = new LinkedHashSet<File>()
        cpElements.addAll(classpath.getFiles())
        cpElements.addAll(classesDirs.getFiles())
        URL[] urls = new URL[cpElements.size()]
        try {
            int i = 0
            for (File cpElement : cpElements) {
                urls[i++] = cpElement.toURI().toURL()
            }
        } catch (MalformedURLException mfue) {
            throw new InvalidUserDataException("failed to build classpath URLs", mfue)
        }
        URLClassLoader urlLoader = null
        ClassLoader loader = urls.length > 0 ?
                (urlLoader = URLClassLoader.newInstance(urls, ClassLoader.getSystemClassLoader())) :
                ClassLoader.getSystemClassLoader()
        try {
            EnumSet<Checker.Option> options = EnumSet.noneOf(Checker.Option)
            if (getFailOnMissingClasses()) {
                options.add(Checker.Option.FAIL_ON_MISSING_CLASSES)
            }
            if (!getIgnoreFailures()) {
                options.add(Checker.Option.FAIL_ON_VIOLATION)
            }
            if (getFailOnUnresolvableSignatures()) {
                options.add(Checker.Option.FAIL_ON_UNRESOLVABLE_SIGNATURES)
            }
            if (getDisableClassloadingCache()) {
                options.add(Checker.Option.DISABLE_CLASSLOADING_CACHE)
            }
            Checker checker = new Checker(loader, options)
            if (!checker.isSupportedJDK) {
                String msg = sprintf(
                        "your Java runtime (%s %s) is not supported by the forbiddenapis plugin. Please run the checks with a supported JDK",
                        System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"))
                if (getFailOnUnsupportedJava()) {
                    throw new GradleException(msg)
                } else {
                    logger.warn(msg)
                    return
                }
            }
            Set<String> suppressAnnotations = getSuppressAnnotations()
            if (suppressAnnotations != null) {
                for (String a : suppressAnnotations) {
                    checker.addSuppressAnnotation(a)
                }
            }
            try {
                Set<String> bundledSignatures = getBundledSignatures()
                if (bundledSignatures != null) {
                    final String bundledSigsJavaVersion = getTargetCompatibility()
                    if (bundledSigsJavaVersion == null) {
                        logger.warn("the 'targetCompatibility' project or task property is missing. " +
                                "Trying to read bundled JDK signatures without compiler target. " +
                                "You have to explicitly specify the version in the resource name")
                    }
                    for (String bs : bundledSignatures) {
                        checker.addBundledSignatures(bs, bundledSigsJavaVersion)
                    }
                }
                if (getInternalRuntimeForbidden()) {
                    logger.warn(DEPRECATED_WARN_INTERNALRUNTIME)
                    checker.addBundledSignatures(BS_JDK_NONPORTABLE, null)
                }

                FileCollection signaturesFiles = getSignaturesFiles()
                if (signaturesFiles != null) for (final File f : signaturesFiles) {
                    checker.parseSignaturesFile(f)
                }
                Set<URL> signaturesURLs = getSignaturesURLs()
                if (signaturesURLs != null) for (final URL url : signaturesURLs) {
                    checker.parseSignaturesFile(url)
                }
                List<String> signatures = getSignatures()
                if (signatures != null && !signatures.isEmpty()) {
                    StringBuilder sb = new StringBuilder()
                    for (String line : signatures) {
                        sb.append(line).append(NL)
                    }
                    checker.parseSignaturesString(sb.toString())
                }
            } catch (IOException ioe) {
                throw new ResourceException("IO exception while reading files with API signatures", ioe)
            } catch (ParseException pe) {
                throw new InvalidUserDataException("Parsing signatures failed: " + pe.getMessage(), pe)
            }

            if (checker.hasNoSignatures()) {
                if (options.contains(Checker.Option.FAIL_ON_UNRESOLVABLE_SIGNATURES)) {
                    throw new InvalidUserDataException("no API signatures found, use properties 'signatures', 'bundledSignatures', 'signaturesURLs', and/or 'signaturesFiles' to define those")
                } else {
                    logger.info("skipping execution because no API signatures are available")
                    return
                }
            }
            try {
                checker.addClassesToCheck(classFiles)
            } catch (IOException ioe) {
                throw new ResourceException("failed to load one of the given class files", ioe)
            }
            checker.run()
        } finally {
            // Java 7 supports closing URLClassLoader, so check for Closeable interface:
            if (urlLoader instanceof Closeable) {
                try {
                    ((Closeable) urlLoader).close()
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }
    }
}
