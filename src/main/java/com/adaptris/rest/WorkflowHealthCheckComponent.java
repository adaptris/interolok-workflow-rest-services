package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.sendErrorResponseQuietly;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.MDC;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.rest.healthcheck.AdapterState;
import com.adaptris.rest.healthcheck.ChannelState;
import com.adaptris.rest.healthcheck.WorkflowState;
import com.adaptris.rest.util.JmxMBeanHelper;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.Lombok;
import lombok.Setter;

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

  @Getter
  @Setter
  private transient JmxMBeanHelper jmxMBeanHelper;

  @Setter
  private String configuredUrlPath;

  public WorkflowHealthCheckComponent() {
    super();
    this.setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, java.util.function.Consumer<AdaptrisMessage> onSuccess) {
    MDC.put(MDC_KEY, friendlyName()); // this is arguably redundant because it's added in the consumer...
    String pathValue = message.getMetadataValue(PATH_KEY);
    String[] pathItem = pathValue.split("/");
    String adapterName = pathItem.length > 2 ? pathItem[2] : null;
    String channelName = pathItem.length > 3 ? pathItem[3] : null;
    String workflowName = pathItem.length > 4 ? pathItem[4] : null;

    try {
      List<AdapterState> states = this.generateStateMap(adapterName, channelName, workflowName);
      String jsonString = new XStreamJsonMarshaller().marshal(states);
      message.setContent(jsonString, message.getContentEncoding());

      this.getConsumer().doResponse(message, message);
    } catch (Exception ex) {
      sendErrorResponseQuietly(getConsumer(), message, ex);
    } finally {
      MDC.remove(MDC_KEY);
    }

  }

  private List<AdapterState> generateStateMap(String adapterName, String channelName, String workflowName)
      throws Exception {
    List<AdapterState> states = new ArrayList<>();
    Set<ObjectInstance> adapterMBeans = this.getJmxMBeanHelper().getMBeans(ADAPTER_OBJ_TYPE_WILD);

    adapterMBeans.forEach(adapterMBean -> {
      try {
        String adapterId = this.getJmxMBeanHelper().getStringAttribute(adapterMBean.getObjectName().toString(), UNIQUE_ID);

        if (adapterName == null || adapterName.equals(adapterId)) {
          String adapterComponentState = this.getJmxMBeanHelper().getStringAttributeClassName(adapterMBean.getObjectName().toString(), COMPONENT_STATE);

          AdapterState adapterState = new AdapterState();
          adapterState.setId(adapterId);
          adapterState.setState(adapterComponentState);

          Set<ObjectName> channels = this.getJmxMBeanHelper().getObjectSetAttribute(adapterMBean.getObjectName().toString(), CHILDREN_ATTRIBUTE);
          for (ObjectName channelObjectName : channels) {
            String channelId = this.getJmxMBeanHelper().getStringAttribute(channelObjectName.toString(), UNIQUE_ID);

            if (channelName == null || channelName.equals(channelId)) {
              String channelComponentState = this.getJmxMBeanHelper().getStringAttributeClassName(channelObjectName.toString(), COMPONENT_STATE);

              ChannelState channelState = new ChannelState();
              channelState.setId(channelId);
              channelState.setState(channelComponentState);

              Set<ObjectName> workflows = this.getJmxMBeanHelper().getObjectSetAttribute(channelObjectName.toString(), CHILDREN_ATTRIBUTE);
              for (ObjectName workflowObjectName : workflows) {
                String workflowId = this.getJmxMBeanHelper().getStringAttribute(workflowObjectName.toString(), UNIQUE_ID);

                if (workflowName == null || workflowName.equals(workflowId)) {
                  String workflowComponentState = this.getJmxMBeanHelper().getStringAttributeClassName(workflowObjectName.toString(), COMPONENT_STATE);

                  WorkflowState workflowState = new WorkflowState();
                  workflowState.setId(workflowId);
                  workflowState.setState(workflowComponentState);

                  channelState.getWorkflowStates().add(workflowState);
                }
              }
              adapterState.getChannelStates().add(channelState);
            }
          }
          states.add(adapterState);
        }
      } catch (Exception ex) {
        log.error("Could not check the health of the running instance.", ex);
        throw Lombok.sneakyThrow(ex);
      }
    });

    return states;
  }

  @Override
  public void init(Properties config) throws Exception {
    super.init(config);
    this.setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

  @Override
  public String getConfiguredUrlPath() {
    return ObjectUtils.defaultIfNull(configuredUrlPath, DEFAULT_PATH);
  }

  @Override
  protected String getAcceptedFilter() {
    return ACCEPTED_FILTER;
  }

}
