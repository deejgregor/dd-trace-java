package datadog.trace.instrumentation.cics;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import com.google.auto.service.AutoService;
import com.ibm.connector2.cics.ECIInteractionSpec;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import net.bytebuddy.asm.Advice;

@AutoService(InstrumenterModule.class)
public final class ECIInteractionInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public ECIInteractionInstrumentation() {
    super("cics");
  }

  @Override
  public String instrumentedType() {
    return "com.ibm.connector2.cics.ECIInteraction";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(named("execute"), getClass().getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope enter(@Advice.Argument(0) final Object spec) {
      if (!(spec instanceof ECIInteractionSpec)) {
        return null;
      }
      final ECIInteractionSpec eciSpec = (ECIInteractionSpec) spec;

      // https://docs.oracle.com/javaee/6/api/constant-values.html#javax.resource.cci.InteractionSpec.SYNC_SEND
      final int verb = eciSpec.getInteractionVerb();
      final String interactionVerb;
      switch (verb) {
        case 0:
          interactionVerb = "SYNC_SEND";
          break;
        case 1:
          interactionVerb = "SYNC_SEND_RECEIVE";
          break;
        case 2:
          interactionVerb = "SYNC_RECEIVE";
          break;
        default:
          interactionVerb = "UNKNOWN_" + verb;
      }

      final String functionName = eciSpec.getFunctionName();
      final String tranName = eciSpec.getTranName();
      final String tpnName = eciSpec.getTPNName();

      AgentSpan span = startSpan("cics", "cics.execute");
      span.setResourceName(interactionVerb + " " + functionName);
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT);
      span.setTag("cics.interaction", interactionVerb);

      if (functionName != null) {
        span.setTag("cics.function", functionName);
      }
      if (tranName != null) {
        span.setTag("cics.tran", tranName);
      }
      if (tpnName != null) {
        span.setTag("cics.tpn", tpnName);
      }

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
