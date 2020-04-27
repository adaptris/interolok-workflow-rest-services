package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.Setter;

@XStreamAlias("channel-state")
public class ChannelState {
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String state;
  @Getter
  @Setter
  private List<WorkflowState> workflowStates;

  public ChannelState() {
    this.setWorkflowStates(new ArrayList<>());
  }

}
