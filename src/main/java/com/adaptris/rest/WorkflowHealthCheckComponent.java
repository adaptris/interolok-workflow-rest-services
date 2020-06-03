package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.CONTENT_TYPE_JSON;
import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;
import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_NOT_READY;
import static com.adaptris.rest.WorkflowServicesConsumer.OK_200;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.ClosedState;
import com.adaptris.core.ComponentState;
import com.adaptris.core.InitialisedState;
import com.adaptris.core.StartedState;
import com.adaptris.core.StoppedState;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.rest.healthcheck.AdapterList;
import com.adaptris.rest.healthcheck.AdapterState;
import com.adaptris.rest.healthcheck.ChannelState;
import com.adaptris.rest.healthcheck.WorkflowState;
import com.adaptris.rest.util.JmxMBeanHelper;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

@XStreamAlias("health-check")
public class WorkflowHealthCheckComponent extends AbstractRestfulEndpoint {

  private static final String BOOTSTRAP_PATH_KEY = "rest.health-check.path";

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/workflow-health-check/*";

  private static final String ADAPTER_OBJ_TYPE_WILD = "com.adaptris:type=Adapter,*";

  private static final String UNIQUE_ID = "UniqueId";

  private static final String CHILDREN_ATTRIBUTE = "Children";

  private static final String COMPONENT_STATE = "ComponentState";

  private static final String PATH_KEY = "jettyURI";

  private static final String LIVENESS_BRANCH = "^(.*)/alive$";
  private static final String READINESS_BRANCH = "^(.*)/ready$";

  // mapping "StartedState" -> StartedState.getInstance()
  private static final List<ComponentState> STATE_LIST =
      Collections.unmodifiableList(Arrays.asList(
          StoppedState.getInstance(),
          InitialisedState.getInstance(),
          StartedState.getInstance(),
          ClosedState.getInstance()));

  private static final Map<String, ComponentState> NAME_TO_COMPONENT_STATE =
      Collections.unmodifiableMap(STATE_LIST.stream()
          .collect(Collectors.toMap((s) -> s.getClass().getSimpleName(), s -> s)));


  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private transient JmxMBeanHelper jmxMBeanHelper;

  @Setter(AccessLevel.PACKAGE)
  private transient XStreamJsonMarshaller marshaller = null;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;


  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  public WorkflowHealthCheckComponent() {
    super();
    marshaller = new XStreamJsonMarshaller();
    setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message,
      java.util.function.Consumer<AdaptrisMessage> onSuccess) {
    // this is arguably redundant because it's added in the consumer...
    MDC.put(MDC_KEY, friendlyName());
    String pathValue = message.getMetadataValue(PATH_KEY);
    try {
      if (pathValue.matches(LIVENESS_BRANCH)) {
        // We are alive, because we got here, so just return a blank payload.
        sendPayload(message, Optional.empty());
      } else if (pathValue.matches(READINESS_BRANCH)) {
        // ready means we need to check all the states, and if something isn't started we return a
        // 503
        buildAdapterStates((id, component) -> {
          throw new NotReadyException(id + " is not started");
        });
        sendPayload(message, Optional.empty());
      } else {
        // otherwise we just get the list of states, and report on them.
        List<AdapterState> states = buildAdapterStates((id, component) -> {
        });
        sendPayload(message, Optional.of(states));
      }
      onSuccess.accept(message);
    } catch (NotReadyException e) {
      sendPayload(message, String.format("{\"failure\": \"%s\"}", e.getMessage()), ERROR_NOT_READY);
    } catch (Exception e) {
      getConsumer().doErrorResponse(message, e, ERROR_DEFAULT);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }


  private void sendPayload(AdaptrisMessage message, String newPayload, int httpStatus) {
    // Since JSON should always be UTF-8
    message.setContent(newPayload, StandardCharsets.UTF_8.name());
    getConsumer().doResponse(message, message, CONTENT_TYPE_JSON, httpStatus);
  }

  private void sendPayload(AdaptrisMessage message, Optional<List<AdapterState>> optState) {
    String newPayload = optState.map((s) -> toString(s)).orElse("");
    sendPayload(message, newPayload, OK_200);
  }

  @SneakyThrows
  protected String toString(List<AdapterState> states) {
    return marshaller.marshal(AdapterList.wrap(states));
  }

  private List<AdapterState> buildAdapterStates(IfNotReady handler) throws Exception {
    List<AdapterState> states = new ArrayList<>();
    Set<ObjectInstance> adapterMBeans = getJmxMBeanHelper().getMBeans(ADAPTER_OBJ_TYPE_WILD);
    for (ObjectInstance adapterMBean : adapterMBeans) {
      states.add(buildAdapterState(adapterMBean, handler));
    }
    return states;
  }

  private AdapterState buildAdapterState(ObjectInstance mbean, IfNotReady handler)
      throws Exception {
    String objRef = mbean.getObjectName().toString();
    String id = getJmxMBeanHelper().getStringAttribute(objRef, UNIQUE_ID);
    String stateStr = getJmxMBeanHelper().getStringAttributeClassName(objRef, COMPONENT_STATE);
    AdapterState report = new AdapterState().withId(id).withState(stateStr);
    verifyReady(id, stateStr, handler);
    Set<ObjectName> channels =
        getJmxMBeanHelper().getObjectSetAttribute(objRef, CHILDREN_ATTRIBUTE);
    addChannelStates(report, channels, handler);
    return report;
  }


  private void addChannelStates(AdapterState adapterState, Set<ObjectName> namedChannels,
      IfNotReady handler) throws Exception {

    for (ObjectName channel : namedChannels) {
      String objRef = channel.toString();
      String id = getJmxMBeanHelper().getStringAttribute(objRef, UNIQUE_ID);
      String stateStr = getJmxMBeanHelper().getStringAttributeClassName(objRef, COMPONENT_STATE);
      ChannelState report = new ChannelState().withId(id).withState(stateStr);
      verifyReady(id, stateStr, handler);
      Set<ObjectName> workflows =
          getJmxMBeanHelper().getObjectSetAttribute(objRef, CHILDREN_ATTRIBUTE);
      addWorkflowStates(report, workflows, handler);
      adapterState.applyDefaultIfNull().add(report);
    }
  }


  private void addWorkflowStates(ChannelState channelState, Set<ObjectName> namedWorkflows,
      IfNotReady handler) throws Exception {
    for (ObjectName workflow : namedWorkflows) {
      String objRef = workflow.toString();
      String id = getJmxMBeanHelper().getStringAttribute(objRef, UNIQUE_ID);
      String stateStr = getJmxMBeanHelper().getStringAttributeClassName(objRef, COMPONENT_STATE);
      WorkflowState report = new WorkflowState().withId(id).withState(stateStr);
      verifyReady(id, stateStr, handler);
      channelState.applyDefaultIfNull().add(report);
    }
  }

  private static void verifyReady(String id, String state, IfNotReady handler)
      throws Exception {
    ComponentState compState = NAME_TO_COMPONENT_STATE.get(state);
    // If we get our states out of sync (i.e. compState = null), then it's not ready.
    if (!StartedState.getInstance().equals(compState)) {
      handler.handle(id, compState);
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    super.init(config);
    setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

  @FunctionalInterface
  private interface IfNotReady {
    void handle(String id, ComponentState state) throws Exception;
  }

  private class NotReadyException extends Exception {
    private static final long serialVersionUID = 2020060201L;

    public NotReadyException(String e) {
      super(e);
    }
  }
}
