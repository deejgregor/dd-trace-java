package datadog.trace.instrumentation.cics;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class JavaGatewayInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public JavaGatewayInstrumentation() {
    super("cics");
  }

  @Override
  public String instrumentedType() {
    return "com.ibm.ctg.client.JavaGateway";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(named("open"), getClass().getName() + "$OpenAdvice");
    transformer.applyAdvice(named("flow"), getClass().getName() + "$FlowAdvice");
  }

  public static class OpenAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope enter(@Advice.FieldValue("strAddress") final String strAddress) {
      AgentSpan parentSpan = activeSpan();

      // If there's already an outbound span active (e.g., from ECIInteractionInstrumentation),
      // just add tags to it instead of creating a new span
      if (parentSpan != null && parentSpan.isOutbound()) {
        if (strAddress != null && parentSpan.getTag("peer.hostname") == null) {
          parentSpan.setTag("peer.hostname", strAddress);
        }
        return null;
      }

      // No client span exists, create a new one
      AgentSpan span = startSpan("cics", "gateway.open");
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
      span.setTag("peer.hostname", strAddress);
      return activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (null != scope) {
        if (throwable != null) {
          scope.span().setError(true);
          scope.span().addThrowable(throwable);
        }
        scope.span().finish();
        scope.close();
      }
    }
  }

  public static class FlowAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope enter(@Advice.FieldValue("strAddress") final String strAddress) {
      AgentSpan parentSpan = activeSpan();

      // If there's already an outbound span active (e.g., from ECIInteractionInstrumentation),
      // just add tags to it instead of creating a new span
      if (parentSpan != null && parentSpan.isOutbound()) {
        if (strAddress != null && parentSpan.getTag("peer.hostname") == null) {
          parentSpan.setTag("peer.hostname", strAddress);
        }
        return null;
      }

      // No client span exists, create a new one
      AgentSpan span = startSpan("cics", "gateway.flow");
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
      span.setTag("peer.hostname", strAddress);
      return activateSpan(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (null != scope) {
        if (throwable != null) {
          scope.span().setError(true);
          scope.span().addThrowable(throwable);
        }
        scope.span().finish();
        scope.close();
      }
    }
  }
}
