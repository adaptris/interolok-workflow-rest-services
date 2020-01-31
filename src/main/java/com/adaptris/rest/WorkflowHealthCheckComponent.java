package com.adaptris.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.commons.lang.ObjectUtils;
import org.codehaus.jettison.json.JSONArray;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.AdaptrisMessageListener;
import com.adaptris.core.CoreException;
import com.adaptris.core.DefaultMessageFactory;
import com.adaptris.core.DefaultSerializableMessageTranslator;
import com.adaptris.core.ServiceException;
import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.core.util.LifecycleHelper;

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

  private transient DefaultSerializableMessageTranslator messageTranslator;

  private transient WorkflowTargetTranslator targetTranslator;

  private transient MBeanServer interlokMBeanServer;

  private transient AdaptrisMessageFactory messageFactory;

  private String configuredUrlPath;

  public WorkflowHealthCheckComponent() {
    this.setConsumer(new HttpRestWorkflowServicesConsumer());
    this.setMessageTranslator(new DefaultSerializableMessageTranslator());
    this.setMessageFactory(DefaultMessageFactory.getDefaultInstance());
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
      JSONArray jsonArray = new JSONArray(states);
      message.setContent(jsonArray.toString(), message.getContentEncoding());

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
    Set<ObjectInstance> adapterMBeans = this.getInterlokMBeanServer().queryMBeans(new ObjectName(ADAPTER_OBJ_TYPE_WILD),
        null);

    adapterMBeans.forEach(adapterMBean -> {
      try {
        String adapterId = (String) getInterlokMBeanServer().getAttribute(adapterMBean.getObjectName(), UNIQUE_ID);

        if ((adapterName.equals(adapterId)) || (adapterName == null)) {
          String adapterComponentState = getInterlokMBeanServer().getAttribute(adapterMBean.getObjectName(), COMPONENT_STATE).getClass().getName();

          AdapterState adapterState = new AdapterState();
          adapterState.setId(adapterId);
          adapterState.setState(adapterComponentState);

          String channels = (String) getInterlokMBeanServer().getAttribute(adapterMBean.getObjectName(), CHILDREN_ATTRIBUTE);
          for (String channelObjectName : channels.split(",")) {
            String channelId = (String) getInterlokMBeanServer().getAttribute(new ObjectName(channelObjectName), UNIQUE_ID);

            if ((channelName.equals(channelId)) || (channelName == null)) {
              String channelComponentState = getInterlokMBeanServer().getAttribute(new ObjectName(channelObjectName), COMPONENT_STATE).getClass().getName();

              ChannelState channelState = new ChannelState();
              channelState.setId(channelId);
              channelState.setState(channelComponentState);

              String workflows = (String) getInterlokMBeanServer().getAttribute(adapterMBean.getObjectName(), CHILDREN_ATTRIBUTE);
              for (String workflowObjectName : workflows.split(",")) {
                String workflowId = (String) getInterlokMBeanServer().getAttribute(new ObjectName(workflowObjectName), UNIQUE_ID);

                if ((workflowName.equals(workflowId)) || (workflowName == null)) {
                  String workflowComponentState = getInterlokMBeanServer().getAttribute(new ObjectName(workflowObjectName), COMPONENT_STATE).getClass().getName();

                  WorkflowState workflowState = new WorkflowState();
                  workflowState.setId(channelId);
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
        new RuntimeException(ex.getMessage());
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
          if (getInterlokMBeanServer() == null)
            setInterlokMBeanServer(JmxHelper.findMBeanServer());

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

  public WorkflowTargetTranslator getTargetTranslator() {
    return targetTranslator;
  }

  public void setTargetTranslator(WorkflowTargetTranslator targetTranslator) {
    this.targetTranslator = targetTranslator;
  }

  public DefaultSerializableMessageTranslator getMessageTranslator() {
    return messageTranslator;
  }

  public void setMessageTranslator(DefaultSerializableMessageTranslator messageTranslator) {
    this.messageTranslator = messageTranslator;
  }

  public MBeanServer getInterlokMBeanServer() {
    return interlokMBeanServer;
  }

  public void setInterlokMBeanServer(MBeanServer interlokMBeanServer) {
    this.interlokMBeanServer = interlokMBeanServer;
  }

  public AdaptrisMessageFactory getMessageFactory() {
    return messageFactory;
  }

  public void setMessageFactory(AdaptrisMessageFactory messageFactory) {
    this.messageFactory = messageFactory;
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

  class AdapterState {
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

  class ChannelState {
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

  class WorkflowState {
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

}
