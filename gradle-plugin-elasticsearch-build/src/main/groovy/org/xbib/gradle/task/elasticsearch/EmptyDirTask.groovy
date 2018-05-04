package org.xbib.gradle.task.elasticsearch

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.nativeintegration.filesystem.Chmod

import javax.inject.Inject

/**
 * Creates an empty directory.
 */
class EmptyDirTask extends DefaultTask {
  @Input
  Object dir

  @Input
  int dirMode = 0755

  @TaskAction
  void create() {
    dir = dir as File
    dir.mkdirs()
    getChmod().chmod(dir, dirMode)
  }

  @Inject
  Chmod getChmod() {
    throw new UnsupportedOperationException()
  }
}
