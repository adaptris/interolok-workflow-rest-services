package com.adaptris.rest.healthcheck;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import com.adaptris.core.util.JmxHelper;

public class JmxMBeanHelper {

  private MBeanServer mBeanServer;
  
  public JmxMBeanHelper() {
  }
  
  public String getStringAttribute(String objectName, String attributeName) throws Exception {
    return (String) mBeanServer().getAttribute(new ObjectName(objectName), attributeName);
  }
  
  public String getStringAttributeClassName(String objectName, String attributeName) throws Exception {
    return (String) mBeanServer().getAttribute(new ObjectName(objectName), attributeName).getClass().getSimpleName();
  }
  
  @SuppressWarnings("unchecked")
  public Set<ObjectName> getObjectSetAttribute(String objectName, String attributeName) throws Exception {
    return (Set<ObjectName>) mBeanServer().getAttribute(new ObjectName(objectName), attributeName);
  }
  
  public Set<ObjectInstance> getMBeans(String objectNameQuery) throws Exception {
    return mBeanServer().queryMBeans(new ObjectName(objectNameQuery), null);
  }

  protected MBeanServer mBeanServer() {
    if(getmBeanServer() == null)
      this.setmBeanServer(JmxHelper.findMBeanServer());
    
    return this.getmBeanServer();
  }
  
  public MBeanServer getmBeanServer() {
    return mBeanServer;
  }

  public void setmBeanServer(MBeanServer mBeanServer) {
    this.mBeanServer = mBeanServer;
  }
}
