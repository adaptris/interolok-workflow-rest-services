package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("channel-state")
public class ChannelState {
  private String id;
  private String state;
  private List<WorkflowState> workflowStates;

  public ChannelState() {
    this.setWorkflowStates(new ArrayList<>());
  }

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

  public List<WorkflowState> getWorkflowStates() {
    return workflowStates;
  }

  public void setWorkflowStates(List<WorkflowState> workflowStates) {
    this.workflowStates = workflowStates;
  }
}
