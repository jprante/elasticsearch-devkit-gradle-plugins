package org.xbib.gradle.task.elasticsearch.qa.forbiddenapis;

import org.apache.tools.ant.ProjectComponent;

public final class SuppressAnnotationType extends ProjectComponent {

  private String classname = null;
  
  public void setClassname(String classname) {
    this.classname = classname;
  }
  
  public String getClassname() {
    return classname;
  }

}
