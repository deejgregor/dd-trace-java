package datadog.trace.core;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MBean implementation for managing and monitoring long-running traces.
 *
 * <p>This class provides JMX operations to inspect currently tracked long-running traces.
 */
public class LongRunningTracesManager implements LongRunningTracesManagerMBean {
  private static final Logger log = LoggerFactory.getLogger(LongRunningTracesManager.class);

  private final LongRunningTracesTracker tracker;

  public LongRunningTracesManager(LongRunningTracesTracker tracker) {
    this.tracker = tracker;
  }

  @Override
  public String getLongRunningTracesJson() throws IOException {
    return tracker.getTracesAsJson();
  }

  @Override
  public int getTrackedTraceCount() {
    return tracker.getTrackedTraceCount();
  }

  @Override
  public long getDroppedTraceCount() {
    return tracker.getDroppedTraceCount();
  }

  @Override
  public long getWrittenTraceCount() {
    return tracker.getWrittenTraceCount();
  }

  @Override
  public long getExpiredTraceCount() {
    return tracker.getExpiredTraceCount();
  }
}
