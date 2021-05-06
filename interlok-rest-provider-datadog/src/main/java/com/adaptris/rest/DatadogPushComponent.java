package com.adaptris.rest;

import java.time.Duration;
import java.util.Properties;

import org.apache.commons.lang3.BooleanUtils;

import com.adaptris.core.management.MgmtComponentImpl;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.core.instrument.Clock;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
public class DatadogPushComponent extends MgmtComponentImpl {

  private static final transient boolean ADDITIONAL_DEBUG = BooleanUtils
      .toBoolean(System.getProperty("interlok.datadog.debug", "false"));
  
  private static final String DATADOG_API_KEY = "datadogApiKey";
  
  private static final String DATADOG_URL_KEY = "datadogUrlKey";
  
  private static final String DATADOG_URL_DEFAULT = "https://api.datadoghq.com";
  
  private static final String DATADOG_PUSH_TIMER_SECONDS_KEY = "datadogPushTimerSeconds";

  private static final String THREAD_NAME = "DatadogPushThread";

  private static final int PUSH_TIMER_SECONDS = 10;

  private volatile boolean running;

  @Getter
  @Setter
  private DatadogMeterRegistry datadogRegistry;

  @Override
  public void init(Properties config) throws Exception {
    DatadogConfig dataDogConfig = new DatadogConfig() {
      @Override
      public Duration step() {
        return Duration.ofSeconds(pushTimerSeconds(config));
      }
      
      @Override
      public String apiKey() {
        return config.getProperty(DATADOG_API_KEY);
      }
      
      @Override
      public String uri() {
        return config.containsKey(DATADOG_URL_KEY) ? config.getProperty(DATADOG_URL_KEY) : DATADOG_URL_DEFAULT;
      }

      @Override
      public String get(String key) {
        return null; // accept the rest of the defaults
      }
    };
    datadogRegistry = new DatadogMeterRegistry(dataDogConfig, Clock.SYSTEM);
  }

  @Override
  public void start() throws Exception {
    running = true;
    new Thread(() -> {
      while (running) {
        MetricProviders.getProviders().forEach(provider -> {
          try {
            provider.bindTo(datadogRegistry);
          } catch (Exception e) {
            log.warn("Metric gathering failed, will try again on next request.");
            exceptionLogging(ADDITIONAL_DEBUG, "Stack trace from metric gathering failure :", e);
          }
        });

        try {
          Thread.sleep(PUSH_TIMER_SECONDS * 1000);
        } catch (InterruptedException e) {
          log.warn("{} interrupted, stopping thread.", THREAD_NAME);
          running = false;
        }
      }
    }, THREAD_NAME).start();
  }

  @Override
  public void stop() throws Exception {
    running = false;
    datadogRegistry.close();
  }

  @Override
  public void destroy() throws Exception {
  }

  int pushTimerSeconds(Properties config) {
    return config.containsKey(DATADOG_PUSH_TIMER_SECONDS_KEY) ?
          Integer.parseInt(config.getProperty(DATADOG_PUSH_TIMER_SECONDS_KEY)) :
          PUSH_TIMER_SECONDS;
  }
  
  // sad, this is for coverage.
  protected void exceptionLogging(boolean logging, String msg, Exception e) {
    if (logging) {
      log.trace(msg, e);
    }
  }

}
