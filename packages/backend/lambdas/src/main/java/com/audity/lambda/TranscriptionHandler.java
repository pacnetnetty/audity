package com.audity.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class TranscriptionHandler implements RequestHandler<SQSEvent, String> {

  @Override
  public String handleRequest(SQSEvent event, Context context) {
    context.getLogger().log("Input: " + event);
    return event.getRecords().size() + " records received.";
  }
}
