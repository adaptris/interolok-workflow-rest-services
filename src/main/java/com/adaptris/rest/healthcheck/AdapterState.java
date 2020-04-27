package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.Setter;

@XStreamAlias("adapter-state")
public class AdapterState {
  @Getter
  @Setter
  private String id;
  @Getter
  @Setter
  private String state;
  @Getter
  @Setter
  private List<ChannelState> channelStates;

  public AdapterState() {
    this.setChannelStates(new ArrayList<>());
  }
}