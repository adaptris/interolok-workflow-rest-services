package com.adaptris.rest.healthcheck;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

@XStreamAlias("adapter-state")
public class AdapterState {
  private String id;
  private String state;
  private List<ChannelState> channelStates;

  public AdapterState() {
    this.setChannelStates(new ArrayList<>());
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

  public List<ChannelState> getChannelStates() {
    return channelStates;
  }

  public void setChannelStates(List<ChannelState> channelStates) {
    this.channelStates = channelStates;
  }

}