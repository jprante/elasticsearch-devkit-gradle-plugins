package org.xbib.gradle.task.elasticsearch.qa.forbiddenapis;

import org.apache.tools.ant.types.resources.Resources;

/** Custom implementation of {@link Resources} to allow adding bundled signatures. */
public final class SignaturesResources extends Resources {
  private final ForbiddenApisAntTask task;
  
  SignaturesResources(ForbiddenApisAntTask task) {
    this.task = task;
  }

  // this is a hack to allow <bundled name="..."/> to be added. This just delegates back to task.
  public BundledSignaturesType createBundled() {
    return task.createBundledSignatures();
  }
  
}
