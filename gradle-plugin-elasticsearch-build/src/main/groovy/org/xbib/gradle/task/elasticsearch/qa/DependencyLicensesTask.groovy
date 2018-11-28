package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A task to check licenses for dependencies.
 *
 * There are two parts to the check:
 * <ul>
 *   <li>LICENSE and NOTICE files</li>
 *   <li>SHA checksums for each dependency jar</li>
 * </ul>
 *
 * The directory to find the license and sha files in defaults to the dir @{code licenses}
 * in the project directory for this task. You can override this directory:
 * <pre>
 *   dependencyLicenses {
 *     licensesDir = project.file('mybetterlicensedir')
 *   }
 * </pre>
 *
 * The jar files to check default to the dependencies from the default configuration. You
 * can override this, for example, to only check compile dependencies:
 * <pre>
 *   dependencyLicenses {
 *     dependencies = project.configurations.compile
 *   }
 * </pre>
 *
 * Every jar must have a {@code .sha1} file in the licenses dir. These can be managed
 * automatically using the {@code updateShas} helper task that is created along
 * with this task. It will add {@code .sha1} files for new jars that are in dependencies
 * and remove old {@code .sha1} files that are no longer needed.
 *
 * Every jar must also have a LICENSE and NOTICE file. However, multiple jars can share
 * LICENSE and NOTICE files by mapping a pattern to the same name.
 * <pre>
 *   dependencyLicenses {
 *     mapping from: &#47;lucene-.*&#47;, to: 'lucene'
 *   }
 * </pre>
 */
class DependencyLicensesTask extends DefaultTask {
    static final String SHA_EXTENSION = '.sha1'

    // TODO: we should be able to default this to eg compile deps, but we need to move the licenses
    // check from distribution to core (ie this should only be run on java projects)

    /**
     * A collection of jar files that should be checked.
     * */
    @InputFiles
    FileCollection dependencies

    /** The directory to find the license and sha files in. */
    @InputDirectory
    File licensesDir = new File(project.projectDir, 'licenses')

    /** A map of patterns to prefix, used to find the LICENSE and NOTICE file. */
    private LinkedHashMap<String, String> mappings = new LinkedHashMap<>()

    /** Names of dependencies whose shas should not exist. */
    private Set<String> ignoreShas = new HashSet<>()

    /**
     * Add a mapping from a regex pattern for the jar name, to a prefix to find
     * the LICENSE and NOTICE file for that jar.
     */
    @Input
    void mapping(Map<String, String> props) {
        String from = props.remove('from')
        if (from == null) {
            throw new InvalidUserDataException('Missing "from" setting for license name mapping')
        }
        String to = props.remove('to')
        if (to == null) {
            throw new InvalidUserDataException('Missing "to" setting for license name mapping')
        }
        if (!props.isEmpty()) {
            throw new InvalidUserDataException("Unknown properties for mapping on dependencyLicenses: ${props.keySet()}")
        }
        mappings.put(from, to)
    }

    /**
     * Add a rule which will skip SHA checking for the given dependency name. This should be used for
     * locally build dependencies, which cause the sha to change constantly.
     */
    @Input
    void ignoreSha(String dep) {
        ignoreShas.add(dep)
    }

    @TaskAction
    void checkDependencies() {
        if (dependencies.isEmpty()) {
            if (licensesDir.exists()) {
                throw new GradleException("Licenses dir ${licensesDir} exists, but there are no dependencies")
            }
            return // no dependencies to check
        } else if (!licensesDir.exists()) {
            throw new GradleException("Licences dir ${licensesDir} does not exist, but there are dependencies")
        }


        // order is the same for keys and values iteration since we use a linked hashmap
        List<String> mapped = new ArrayList<>(mappings.values())
        Pattern mappingsPattern = Pattern.compile('(' + mappings.keySet().join(')|(') + ')')
        Map<String, Integer> licenses = new HashMap<>()
        Map<String, Integer> notices = new HashMap<>()
        Set<File> shaFiles = new HashSet<File>()

        licensesDir.eachFile {
            String name = it.getName()
            if (name.endsWith(SHA_EXTENSION)) {
                shaFiles.add(it)
            } else if (name.endsWith('-LICENSE') || name.endsWith('-LICENSE.txt')) {
                licenses.put(name, 0)
            } else if (name.contains('-NOTICE') || name.contains('-NOTICE.txt')) {
                notices.put(name, 0)
            }
        }

        for (File dependency : dependencies) {
            String jarName = dependency.getName()
            String depName = jarName - ~/\-\d+.*/
            if (ignoreShas.contains(depName)) {
                // local deps should not have sha files!
                if (getShaFile(jarName).exists()) {
                    throw new GradleException("SHA file ${getShaFile(jarName)} exists for ignored dependency ${depName}")
                }
            } else {
                logger.info("Checking sha for " + jarName)
                checkSha(dependency, jarName, shaFiles)
            }

            logger.info("Checking license/notice for " + depName)
            Matcher match = mappingsPattern.matcher(depName)
            if (match.matches()) {
                int i = 0
                while (i < match.groupCount() && match.group(i + 1) == null) ++i;
                logger.info("Mapped dependency name ${depName} to ${mapped.get(i)} for license check")
                depName = mapped.get(i)
            }
            checkFile(depName, jarName, licenses, 'LICENSE')
            checkFile(depName, jarName, notices, 'NOTICE')
        }

        licenses.each { license, count ->
            if (count == 0) {
                logger.warn("Unused license ${license}")
            }
        }
        notices.each { notice, count ->
            if (count == 0) {
                logger.warn("Unused notice ${notice}")
            }
        }
        if (!shaFiles.isEmpty()) {
            logger.warn("Unused sha files found: \n${shaFiles.join('\n')}")
        }
    }

    private File getShaFile(String jarName) {
        return new File(licensesDir, jarName + SHA_EXTENSION)
    }

    private void checkSha(File jar, String jarName, Set<File> shaFiles) {
        File shaFile = getShaFile(jarName)
        if (!shaFile.exists()) {
            throw new GradleException("Missing SHA for ${jarName}. Run 'gradle updateSHAs' to create")
        }
        String expectedSha = shaFile.getText('UTF-8').trim()
        String sha = MessageDigest.getInstance("SHA-1").digest(jar.getBytes()).encodeHex().toString()
        if (!expectedSha.equals(sha)) {
            throw new GradleException("SHA has changed! Expected ${expectedSha} for ${jarName} but got ${sha}. " +
                                      "\nThis usually indicates a corrupt dependency cache or artifacts changed upstream." +
                                      "\nEither wipe your cache, fix the upstream artifact, or delete ${shaFile} and run updateShas")
        }
        shaFiles.remove(shaFile)
    }

    private void checkFile(String name, String jarName, Map<String, Integer> counters, String type) {
        String fileName = "${name}-${type}"
        Integer count = counters.get(fileName)
        if (count == null) {
            fileName = "${fileName}.txt"
            counters.get(fileName)
        }
        count = counters.get(fileName)
        if (count == null) {
            throw new GradleException("Missing ${type} for ${jarName}, expected in ${fileName}")
        }
        counters.put(fileName, count + 1)
    }

    /**
     * A helper task to update the sha files in the license dir.
     */
    static class UpdateShasTask extends DefaultTask {
        private DependencyLicensesTask parentTask

        @TaskAction
        void updateShas() {
            Set<File> shaFiles = new HashSet<File>()
            parentTask.licensesDir.eachFile {
                String name = it.getName()
                if (name.endsWith(SHA_EXTENSION)) {
                    shaFiles.add(it)
                }
            }
            for (File dependency : parentTask.dependencies) {
                String jarName = dependency.getName()
                String depName = jarName - ~/\-\d+.*/
                if (parentTask.ignoreShas.contains(depName)) {
                    continue
                }
                File shaFile = new File(parentTask.licensesDir, jarName + SHA_EXTENSION)
                if (!shaFile.exists()) {
                    logger.lifecycle("Adding sha for ${jarName}")
                    String sha = MessageDigest.getInstance("SHA-1").digest(dependency.getBytes()).encodeHex().toString()
                    shaFile.setText(sha, 'UTF-8')
                } else {
                    shaFiles.remove(shaFile)
                }
            }
            shaFiles.each { shaFile ->
                logger.lifecycle("Removing unused sha ${shaFile.getName()}")
                Files.delete(shaFile.toPath())
            }
        }
    }
}
