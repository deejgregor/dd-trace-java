package datadog.trace.instrumentation.sslsocket;

import com.google.auto.service.*;
import datadog.context.*;
import datadog.trace.agent.tooling.*;
import datadog.trace.api.*;
import datadog.trace.api.sampling.*;
import datadog.trace.bootstrap.debugger.*;
import datadog.trace.bootstrap.instrumentation.api.*;
import datadog.trace.core.*;
import net.bytebuddy.asm.*;

import java.time.*;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

@AutoService(InstrumenterModule.class)
public class SocketConnectTracingInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public SocketConnectTracingInstrumentation() {
    super("sockettracing");
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().
        isConnectionDetailsTracingEnabled();
  }

  @Override
  public String instrumentedType() {
    return "java.net.Socket";
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
      transformer.applyAdvice(
        isMethod()
            .and(named("connect"))
            .and(takesArgument(0, named("java.net.SocketAddress")))
            .and(takesArgument(1, int.class)),
        getClass().getName() + "$ConnectAdvice");
  }

  public static final class ConnectAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope before() {
      AgentSpan span = startSpan("tcp.connect");
      return activateSpan(span);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void after(@Advice.Enter final AgentScope scope) {
      AgentSpan span = scope.span();
      scope.close();
      span.finish();
    }
  }
}
