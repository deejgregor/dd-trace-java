import com.ibm.ctg.client.JavaGateway
import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan

class JavaGatewayInstrumentationTest extends InstrumentationSpecification {

  def "open with no parent"() {
    when:
    try {
      new JavaGateway("localhost", 1234)
    } catch (IOException ignored) {
      // expected
    }

    then:
    assertTraces(1) {
      trace(1) {
        span(0) {
          operationName "gateway.open"
          errored true
          tags {
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "peer.hostname" "localhost"
            errorTags IOException, String
            defaultTags()
          }
        }
      }
    }
  }
  def "open with parent client span"() {
    when:
    try {
      runUnderTrace("parent") {
        activeSpan().setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
        new JavaGateway("localhost", 1234)
      }
    } catch (IOException ignored) {
      // expected
    }

    then:
    assertTraces(1) {
      trace(1) {
        span(0) {
          operationName "parent"
          errored true
          tags {
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "peer.hostname" "localhost"
            errorTags IOException, String
            defaultTags()
          }
        }
      }
    }
  }

  def "open with parent non-client span"() {
    when:
    runUnderTrace("parent") {
      try {
        def gateway = new JavaGateway("localhost", 1234)
        gateway.open()
      } catch (IOException ignored) {
        // expected
      }
    }

    then:
    assertTraces(1) {
      trace(2) {
        basicSpan(it, "parent")
        span(1) {
          operationName "gateway.open"
          childOf(span(0))
          errored true
          tags {
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "peer.hostname" "localhost"
            errorTags IOException, String
            defaultTags()
          }
        }
      }
    }
  }
}
