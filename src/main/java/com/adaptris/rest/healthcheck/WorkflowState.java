package com.adaptris.rest.healthcheck;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@XStreamAlias("workflow-state")
@NoArgsConstructor
public class WorkflowState {
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String state;

}
