package datadog.trace.instrumentation.play25.appsec;

import static datadog.trace.api.gateway.Events.EVENTS;

import com.fasterxml.jackson.databind.JsonNode;
import datadog.appsec.api.blocking.BlockingException;
import datadog.trace.advice.ActiveRequestContext;
import datadog.trace.advice.RequiresRequestContext;
import datadog.trace.api.gateway.BlockResponseFunction;
import datadog.trace.api.gateway.CallbackProvider;
import datadog.trace.api.gateway.Flow;
import datadog.trace.api.gateway.RequestContext;
import datadog.trace.api.gateway.RequestContextSlot;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import java.util.function.BiFunction;
import net.bytebuddy.asm.Advice;
import play.mvc.StatusHeader;

@RequiresRequestContext(RequestContextSlot.APPSEC)
public class StatusHeaderSendJsonAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  static void before() {
    CallDepthThreadLocalMap.incrementCallDepth(StatusHeader.class);
  }

  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  static void after(
      @Advice.Argument(0) final JsonNode json, @ActiveRequestContext RequestContext reqCtx) {
    final int depth = CallDepthThreadLocalMap.decrementCallDepth(StatusHeader.class);
    if (depth > 0) {
      return;
    }

    if (json == null) {
      return;
    }

    CallbackProvider cbp = AgentTracer.get().getCallbackProvider(RequestContextSlot.APPSEC);
    if (cbp == null) {
      return;
    }
    BiFunction<RequestContext, Object, Flow<Void>> callback =
        cbp.getCallback(EVENTS.responseBody());
    if (callback == null) {
      return;
    }

    Flow<Void> flow = callback.apply(reqCtx, json);
    Flow.Action action = flow.getAction();
    if (action instanceof Flow.Action.RequestBlockingAction) {
      BlockResponseFunction blockResponseFunction = reqCtx.getBlockResponseFunction();
      if (blockResponseFunction == null) {
        return;
      }
      Flow.Action.RequestBlockingAction rba = (Flow.Action.RequestBlockingAction) action;
      blockResponseFunction.tryCommitBlockingResponse(
          reqCtx.getTraceSegment(),
          rba.getStatusCode(),
          rba.getBlockingContentType(),
          rba.getExtraHeaders());

      throw new BlockingException("Blocked request (for StatusHeader/sendJson)");
    }
  }
}
