package com.adaptris.rest.healthcheck;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AdapterStateTest {

  @Test
  public void testWithChannelStates() {
    AdapterState state = new AdapterState().withId("id").withState("state");
    assertNull(state.getChannelStates());
    state.withChannelStates(new ArrayList());
    assertNotNull(state.getChannelStates());
  }

  @Test
  public void testApplyDefaultIfNull() {
    AdapterState state = new AdapterState();
    assertNull(state.getChannelStates());
    List<ChannelState> s1 = state.applyDefaultIfNull();
    List<ChannelState> s2 = state.applyDefaultIfNull();
    assertSame(s1, s2);
    assertNotNull(state.getChannelStates());
  }
}
