package datadog.trace.core;

import java.io.IOException;

/**
 * MBean interface for managing and monitoring pending traces in the buffer.
 *
 * <p>This interface provides JMX operations to inspect currently buffered pending traces.
 */
public interface PendingTracesManagerMBean {

  /**
   * Returns detailed information about all currently buffered pending traces as JSON.
   *
   * @return JSON string containing an array of pending trace details
   * @throws IOException if an error occurs while generating JSON
   */
  String getPendingTracesJson() throws IOException;

  /**
   * Returns the number of currently buffered pending traces.
   *
   * @return the count of pending traces in the buffer
   */
  int getPendingTraceCount();
}
