package com.adaptris.rest.healthcheck;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("workflow-state")
public class WorkflowState {
  private String id;
  private String state;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }
}
