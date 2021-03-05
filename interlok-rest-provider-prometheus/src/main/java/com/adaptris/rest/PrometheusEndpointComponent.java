package com.adaptris.rest;

import static com.adaptris.rest.WorkflowServicesConsumer.ERROR_DEFAULT;

import java.util.function.Consumer;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.rest.metrics.MetricProviders;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import lombok.AccessLevel;
import lombok.Getter;

public class PrometheusEndpointComponent  extends AbstractRestfulEndpoint {
  
  private static final String ACCEPTED_FILTER = "GET";

  private static final String DEFAULT_PATH = "/prometheus/metrics/*";

  @Getter(AccessLevel.PROTECTED)
  private transient final String defaultUrlPath = DEFAULT_PATH;

  @Getter(AccessLevel.PROTECTED)
  private transient final String acceptedFilter = ACCEPTED_FILTER;
  
  @Override
  public void onAdaptrisMessage(AdaptrisMessage message, Consumer<AdaptrisMessage> success, Consumer<AdaptrisMessage> failure) {
    PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    prometheusRegistry.config().meterFilter(new PrometheusRenameFilter());
    
    try {
      MetricProviders.getProviders().forEach( provider -> {
        try {
          provider.bindTo(prometheusRegistry);
        } catch (Exception e) {
          log.warn("Metric gathering failed, will try again on next request.", e);
        }
      });
      
      message.setContent(prometheusRegistry.scrape(), message.getContentEncoding());
      
      getConsumer().doResponse(message, message);
      success.accept(message);
      
      prometheusRegistry.clear();
    } catch (Exception ex) {
      getConsumer().doErrorResponse(message, ex, ERROR_DEFAULT);
      failure.accept(message);
    }
  }

  

}
