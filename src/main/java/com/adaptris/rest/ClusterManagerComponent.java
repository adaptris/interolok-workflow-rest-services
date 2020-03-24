package com.adaptris.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.ObjectUtils;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.XStreamJsonMarshaller;
import com.adaptris.mgmt.cluster.ClusterInstance;
import com.adaptris.mgmt.cluster.mbean.ClusterManagerMBean;
import com.adaptris.rest.util.JmxMBeanHelper;

public class ClusterManagerComponent extends AbstractRestfulEndpoint {

  private static final String BOOTSTRAP_PATH_KEY = "rest.cluster-manager.path";

  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/cluster-manager/*";

  private static final String CLUSTER_MANAGER_OBJECT_NAME = "com.adaptris:type=ClusterManager,id=ClusterManager";
  
  private transient JmxMBeanHelper jmxMBeanHelper;

  private String configuredUrlPath;

  public ClusterManagerComponent () {
    super();
    this.setJmxMBeanHelper(new JmxMBeanHelper());
  }

  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, java.util.function.Consumer<AdaptrisMessage> onSuccess) {
    try {
      ClusterManagerMBean clusterManager = this.getJmxMBeanHelper().proxyMBean(CLUSTER_MANAGER_OBJECT_NAME, ClusterManagerMBean.class);
      
      final List<ClusterInstance> clusterInstances = new ArrayList<ClusterInstance>();
      clusterManager.getClusterInstances().getKeys().forEach(key -> {
        try {
          clusterInstances.add((ClusterInstance) clusterManager.getClusterInstances().get(key));
        } catch (CoreException e) {}
      });
      String jsonString = new XStreamJsonMarshaller().marshal(clusterInstances);
      message.setContent(jsonString, message.getContentEncoding());
      
      this.getConsumer().doResponse(message, message);
    } catch (Exception ex) {
      try {
        this.getConsumer().doErrorResponse(message, ex);
      } catch (ServiceException e) {
      }
    }
  }

  @Override
  public void init(Properties config) throws Exception {
    super.init(config);
    this.setConfiguredUrlPath(config.getProperty(BOOTSTRAP_PATH_KEY));
  }

  public String getConfiguredUrlPath() {
    return ObjectUtils.defaultIfNull(configuredUrlPath, DEFAULT_PATH);
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

  @Override
  protected String getAcceptedFilter() {
    return ACCEPTED_FILTER;
  }
}
