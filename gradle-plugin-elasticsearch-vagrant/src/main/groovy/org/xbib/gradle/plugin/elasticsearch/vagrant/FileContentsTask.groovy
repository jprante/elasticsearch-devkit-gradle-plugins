package org.xbib.gradle.plugin.elasticsearch.vagrant

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Creates a file and sets it contents to something.
 */
class FileContentsTask extends DefaultTask {
  /**
   * The file to be built. Must be of type File to make @OutputFile happy.
   */
  @OutputFile
  File file

  @Input
  Object contents

  /**
   * The file to be built. Takes any objecct and coerces to a file.
   */
  void setFile(Object file) {
    this.file = file as File
  }

  @TaskAction
  void setContents() {
    file = file as File
    file.text = contents.toString()
  }
}
