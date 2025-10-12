package datadog.trace.core;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MBean implementation for managing and monitoring pending traces in the buffer.
 *
 * <p>This class provides JMX operations to inspect currently buffered pending traces.
 */
public class PendingTracesManager implements PendingTracesManagerMBean {
  private static final Logger log = LoggerFactory.getLogger(PendingTracesManager.class);

  private final PendingTraceBuffer buffer;

  public PendingTracesManager(PendingTraceBuffer buffer) {
    this.buffer = buffer;
  }

  @Override
  public String getPendingTracesJson() throws IOException {
    return buffer.getTracesAsJson();
  }

  @Override
  public int getPendingTraceCount() {
    return buffer.getPendingTraceCount();
  }
}
