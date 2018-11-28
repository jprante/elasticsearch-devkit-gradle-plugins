package org.xbib.gradle.task.elasticsearch.qa.forbiddenapis;

import org.apache.tools.ant.ProjectComponent;

public final class BundledSignaturesType extends ProjectComponent {

  private String name = null;
  private String targetVersion = null;
  
  public void setName(String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }

  public void setTargetVersion(String targetVersion) {
    this.targetVersion = targetVersion;
  }
  
  public String getTargetVersion() {
    return targetVersion;
  }

}
