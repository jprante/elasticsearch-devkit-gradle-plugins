package org.xbib.gradle.task.elasticsearch

import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Creates an empty directory.
 */
class EmptyDirTask extends Exec {
  @Input
  Object dir

  @Input
  int dirMode = 0755

  @TaskAction
  void create() {
    dir = dir as File
    dir.mkdirs()
    commandLine 'chmod', String.format('%o', dirMode), dir
  }
}
