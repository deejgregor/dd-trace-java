package datadog.trace.instrumentation.sslsocket;

import com.google.auto.service.*;
import datadog.trace.agent.tooling.*;
import datadog.trace.api.*;
import datadog.trace.bootstrap.instrumentation.api.*;
import datadog.trace.bootstrap.instrumentation.sslsocket.*;
import datadog.trace.bootstrap.instrumentation.usm.*;
import net.bytebuddy.asm.*;
import net.bytebuddy.description.type.*;
import net.bytebuddy.matcher.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.*;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

@AutoService(InstrumenterModule.class)
public final class SslSocketHandshakeInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForBootstrap,
        Instrumenter.ForTypeHierarchy,
        Instrumenter.HasMethodAdvice {

  public SslSocketHandshakeInstrumentation() {
    super("sslsockethandshake");
  }

  @Override
  protected boolean defaultEnabled() {
    return InstrumenterConfig.get().
        isConnectionDetailsTracingEnabled();
  }

  @Override
  public String hierarchyMarkerType() {
    return null;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return extendsClass(named("javax.net.ssl.SSLSocket")).and(concreteClass());
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
      transformer.applyAdvice(
        isMethod().and(named("startHandshake").and(takesArguments(1))), // FIXME this is specific to sun.security.ssl.SSLSocketImpl
        SslSocketHandshakeInstrumentation.class.getName() + "$StartHandshakeAdvice");
  }

  public static final class StartHandshakeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter() {
      AgentSpan span = startSpan("tls.negotiate");
      return activateSpan(span);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Enter final AgentScope scope) {
      AgentSpan span = scope.span();
      scope.close();
      span.finish();
    }
  }
}
