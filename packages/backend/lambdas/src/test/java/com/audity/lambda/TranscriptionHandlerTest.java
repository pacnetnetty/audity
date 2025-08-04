package com.audity.lambda;

import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.audity.lambda.util.LambdaResponse;
import com.audity.lambda.util.MockContext;
import org.junit.jupiter.api.Test;

public class TranscriptionHandlerTest {

  @Test
  public void testHandlerFunction() {
    // Arrange
    var handler = new TranscriptionHandler();
    // Add any necessary setup for the test

    // Act
    var event = new SQSEvent();
    event.setRecords(java.util.Collections.emptyList());

    LambdaResponse resp = handler.handleRequest(event, new MockContext());

    // Assert
    assertNotNull(resp);
  }
}
