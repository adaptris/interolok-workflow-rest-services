package com.adaptris.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.commons.lang3.ObjectUtils;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.rest.healthcheck.AdapterState;
import com.adaptris.rest.healthcheck.ChannelState;
import com.adaptris.rest.healthcheck.JmxMBeanHelper;
import com.adaptris.rest.healthcheck.WorkflowState;
import com.thoughtworks.xstream.annotations.XStreamAlias;

import lombok.Lombok;

@XStreamAlias("health-check")
public class WorkflowHealthCheckComponent extends MgmtComponentImpl implements AdaptrisMessageListener {

  private static final String BOOTSTRAP_PATH_KEY = "rest.health-check.path";

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/workflow-health-check/*";

  private static final String ADAPTER_OBJ_TYPE_WILD = "com.adaptris:type=Adapter,*";

  private static final String UNIQUE_ID = "UniqueId";

  private static final String CHILDREN_ATTRIBUTE = "Children";

  private static final String COMPONENT_STATE = "ComponentState";

  private static final String PATH_KEY = "jettyURI";

  private transient WorkflowServicesConsumer consumer;
  
  private transient JmxMBeanHelper jmxMBeanHelper;

  private String configuredUrlPath;

  public WorkflowHealthCheckComponent() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
    this.setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message) {
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
      try {
        this.getConsumer().doErrorResponse(message, ex);
      } catch (ServiceException e) {
      }
    }

  }

  private List<AdapterState> generateStateMap(String adapterName, String channelName, String workflowName)
      throws Exception {
    List<AdapterState> states = new ArrayList<>();
    Set<ObjectInstance> adapterMBeans = this.getJmxMBeanHelper().getMBeans(ADAPTER_OBJ_TYPE_WILD);

    adapterMBeans.forEach(adapterMBean -> {
      try {
        String adapterId = this.getJmxMBeanHelper().getStringAttribute(adapterMBean.getObjectName().toString(), UNIQUE_ID);

        if ((adapterName == null) || (adapterName.equals(adapterId))) {
          String adapterComponentState = this.getJmxMBeanHelper().getStringAttributeClassName(adapterMBean.getObjectName().toString(), COMPONENT_STATE);

          AdapterState adapterState = new AdapterState();
          adapterState.setId(adapterId);
          adapterState.setState(adapterComponentState);

          Set<ObjectName> channels = this.getJmxMBeanHelper().getObjectSetAttribute(adapterMBean.getObjectName().toString(), CHILDREN_ATTRIBUTE);
          for (ObjectName channelObjectName : channels) {
            String channelId = this.getJmxMBeanHelper().getStringAttribute(channelObjectName.toString(), UNIQUE_ID);

            if ((channelName == null) || (channelName.equals(channelId))) {
              String channelComponentState = this.getJmxMBeanHelper().getStringAttributeClassName(channelObjectName.toString(), COMPONENT_STATE);

              ChannelState channelState = new ChannelState();
              channelState.setId(channelId);
              channelState.setState(channelComponentState);

              Set<ObjectName> workflows = this.getJmxMBeanHelper().getObjectSetAttribute(channelObjectName.toString(), CHILDREN_ATTRIBUTE);
              for (ObjectName workflowObjectName : workflows) {
                String workflowId = this.getJmxMBeanHelper().getStringAttribute(workflowObjectName.toString(), UNIQUE_ID);

                if ((workflowName == null) || (workflowName.equals(workflowId))) {
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
    this.setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

  @Override
  public void start() throws Exception {
    WorkflowHealthCheckComponent instance = this;
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          getConsumer().setAcceptedHttpMethods(ACCEPTED_FILTER);
          getConsumer().setConsumedUrlPath(configuredUrlPath());
          getConsumer().setMessageListener(instance);
          getConsumer().prepare();
          LifecycleHelper.init(getConsumer());
          LifecycleHelper.start(getConsumer());

          log.debug("Workflow REST services component started.");
        } catch (CoreException e) {
          log.error("Could not start the Workflow REST services component.", e);
        }
      }
    }).start();

  }

  @Override
  public void stop() throws Exception {
    LifecycleHelper.stop(getConsumer());
  }

  @Override
  public void destroy() throws Exception {
    LifecycleHelper.close(getConsumer());
  }

  @Override
  public String friendlyName() {
    return this.getClass().getSimpleName();
  }

  public WorkflowServicesConsumer getConsumer() {
    return consumer;
  }

  public void setConsumer(WorkflowServicesConsumer consumer) {
    this.consumer = consumer;
  }

  String configuredUrlPath() {
    return (String) ObjectUtils.defaultIfNull(this.getConfiguredUrlPath(), DEFAULT_PATH);
  }

  public String getConfiguredUrlPath() {
    return configuredUrlPath;
  }

  public void setConfiguredUrlPath(String configuredUrlPath) {
    this.configuredUrlPath = configuredUrlPath;
  }

  public JmxMBeanHelper getJmxMBeanHelper() {
    return jmxMBeanHelper;
  }

  public void setJmxMBeanHelper(JmxMBeanHelper jmxMBeanHelper) {
    this.jmxMBeanHelper = jmxMBeanHelper;
  }

}
