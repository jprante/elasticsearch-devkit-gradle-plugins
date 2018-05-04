package org.xbib.gradle.task.elasticsearch

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Concatenates a list of files into one and removes duplicate lines.
 */
class ConcatFilesTask extends DefaultTask {

    /** List of files to concatenate */
    @InputFiles
    FileTree files

    /** line to add at the top of the target file */
    @Input
    @Optional
    String headerLine

    @OutputFile
    File target

    ConcatFilesTask() {
        description = 'Concat a list of files into one.'
    }

    @TaskAction
    void concatFiles() {
        final StringBuilder output = new StringBuilder()

        if (headerLine) {
            output.append(headerLine).append('\n')
        }

        final StringBuilder sb = new StringBuilder()
        files.each { file ->
            sb.append(file.getText('UTF-8'))
        }
        // Remove duplicate lines
        sb.readLines().toSet().each { value ->
            output.append(value).append('\n')
        }

        target.setText(output.toString(), 'UTF-8')
    }
}
