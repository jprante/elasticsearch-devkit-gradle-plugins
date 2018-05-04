package org.xbib.gradle.task.elasticsearch.qa

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

import java.util.regex.Pattern

/**
 * Checks for patterns in source files for the project which are forbidden.
 */
class ForbiddenPatternsTask extends DefaultTask {

    /** The rules: a map from the rule name, to a rule regex pattern. */
    private Map<String,String> patterns = new LinkedHashMap<>()
    /** A pattern set of which files should be checked. */
    private PatternFilterable filesFilter = new PatternSet()

    @OutputFile
    File outputMarker = new File(project.buildDir, "markers/forbiddenPatterns")

    ForbiddenPatternsTask() {
        description = 'Checks source files for invalid patterns like nocommits or tabs'

        // we include knnown source files
        filesFilter.include('**/*.java')
        filesFilter.include('**/*.scala')
        filesFilter.include('**/*.clj')
        filesFilter.include('**/*.groovy')
        filesFilter.include('**/*.kt')
        filesFilter.include('**/*.js')
        filesFilter.include('**/*.css')
        filesFilter.include('**/*.txt')
        filesFilter.include('**/*.html')
        filesFilter.include('**/*.xml')
        // add mandatory rules
        patterns.put('nocommit', /nocommit|NOCOMMIT/)
        patterns.put('nocommit should be all lowercase or all uppercase',
            /((?i)nocommit)(?<!(nocommit|NOCOMMIT))/)
        patterns.put('tab', /\t/)
        inputs.property("excludes", filesFilter.excludes)
        inputs.property("rules", patterns)
    }

    /** Adds a file glob pattern to be excluded */
    void exclude(String... excludes) {
        filesFilter.exclude(excludes)
    }

    /** Adds a pattern to forbid. T */
    void rule(Map<String,String> props) {
        String name = props.remove('name')
        if (name == null) {
            throw new InvalidUserDataException('Missing [name] for invalid pattern rule')
        }
        String pattern = props.remove('pattern')
        if (pattern == null) {
            throw new InvalidUserDataException('Missing [pattern] for invalid pattern rule')
        }
        if (!props.isEmpty()) {
            throw new InvalidUserDataException("Unknown arguments for ForbiddenPatterns rule mapping: ${props.keySet()}")
        }
        // TODO: fail if pattern contains a newline, it won't work (currently)
        patterns.put(name, pattern)
    }

    /** Returns the files this task will check */
    @InputFiles
    FileCollection files() {
        List<FileCollection> collections = new ArrayList<>()
        for (SourceSet sourceSet : project.sourceSets) {
            collections.add(sourceSet.allSource.matching(filesFilter))
        }
        return project.files(collections.toArray())
    }

    @TaskAction
    void checkInvalidPatterns() {
        Pattern allPatterns = Pattern.compile('(' + patterns.values().join(')|(') + ')')
        List<String> failures = new ArrayList<>()
        for (File f : files()) {
            f.eachLine('UTF-8') { String line, int lineNumber ->
                if (allPatterns.matcher(line).find()) {
                    addErrorMessages(failures, f, line, lineNumber)
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new GradleException('Found invalid patterns:\n' + failures.join('\n'))
        }
        outputMarker.setText('done', 'UTF-8')
    }

    // iterate through patterns to find the right ones for nice error messages
    void addErrorMessages(List<String> failures, File f, String line, int lineNumber) {
        String path = project.getRootProject().projectDir.toURI().relativize(f.toURI()).toString()
        for (Map.Entry<String,String> pattern : patterns.entrySet()) {
            if (Pattern.compile(pattern.value).matcher(line).find()) {
                failures.add('- ' + pattern.key + ' on line ' + lineNumber + ' of ' + path)
            }
        }
    }
}
