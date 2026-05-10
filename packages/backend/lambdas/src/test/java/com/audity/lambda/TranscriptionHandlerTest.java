package com.audity.lambda;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("TranscriptionHandler Tests")
public class TranscriptionHandlerTest {

  private TranscriptionHandler handler;

  @Mock private Context mockContext;
  @Mock private LambdaLogger mockLogger;
  @Mock private TranscribeClient mockTranscribeClient;
  @Mock private ExecutorService mockExecutorService;
  @Mock private StartTranscriptionJobResponse mockTranscriptionResponse;

  @BeforeEach
  public void setUp() {
    when(mockContext.getLogger()).thenReturn(mockLogger);
    handler = new TranscriptionHandler(mockTranscribeClient, mockExecutorService);
  }

  // ── Helpers
  // ───────────────────────────────────────────────────────────────────

  private String s3EventJson(String bucket, String key) {
    return String.format(
        "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"%s\"},\"object\":{\"key\":\"%s\"}}}]}",
        bucket, key);
  }

  private SQSMessage sqsMessage(String messageId, String bucket, String key) {
    SQSMessage msg = new SQSMessage();
    msg.setMessageId(messageId);
    msg.setBody(s3EventJson(bucket, key));
    return msg;
  }

  private Future<StartTranscriptionJobResponse> successFuture() {
    return CompletableFuture.completedFuture(mockTranscriptionResponse);
  }

  private Future<StartTranscriptionJobResponse> executionExceptionFuture(String message) {
    CompletableFuture<StartTranscriptionJobResponse> future = new CompletableFuture<>();
    future.completeExceptionally(
        new ExecutionException(message, new Exception("Service unavailable")));
    return future;
  }

  private Future<StartTranscriptionJobResponse> interruptedFuture() {
    return new CompletableFuture<>() {
      @Override
      public StartTranscriptionJobResponse get() throws InterruptedException {
        throw new InterruptedException("Thread interrupted");
      }
    };
  }

  // ── Tests
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Empty event returns empty failures list")
  public void testEmptyEvent() {
    SQSEvent event = new SQSEvent();
    event.setRecords(new ArrayList<>());

    SQSBatchResponse response = handler.handleRequest(event, mockContext);

    assertNotNull(response);
    assertTrue(response.getBatchItemFailures().isEmpty());
  }

  @Test
  @DisplayName("Single valid message submits one job and returns no failures")
  public void testSingleValidMessage() {
    SQSEvent event = new SQSEvent();
    event.setRecords(List.of(sqsMessage("msg-1", "test-bucket", "inputs/kb-123/audio.mp3")));

    when(mockExecutorService.<StartTranscriptionJobResponse>submit(any(Callable.class)))
        .thenReturn(successFuture());

    SQSBatchResponse response = handler.handleRequest(event, mockContext);

    assertNotNull(response);
    assertTrue(response.getBatchItemFailures().isEmpty());
    verify(mockExecutorService, times(1)).submit(any(Callable.class));
  }

  @Test
  @DisplayName("Multiple valid messages each submit a job and return no failures")
  public void testMultipleValidMessages() {
    SQSEvent event = new SQSEvent();
    event.setRecords(
        List.of(
            sqsMessage("msg-1", "test-bucket", "inputs/kb-123/audio1.mp3"),
            sqsMessage("msg-2", "test-bucket", "inputs/kb-456/audio2.mp3")));

    when(mockExecutorService.<StartTranscriptionJobResponse>submit(any(Callable.class)))
        .thenReturn(successFuture())
        .thenReturn(successFuture());

    SQSBatchResponse response = handler.handleRequest(event, mockContext);

    assertNotNull(response);
    assertTrue(response.getBatchItemFailures().isEmpty());
    verify(mockExecutorService, times(2)).submit(any(Callable.class));
  }

  @Test
  @DisplayName("ExecutionException marks message as batch item failure")
  public void testExecutionExceptionReportedAsFailure() {
    String messageId = "msg-failure";
    SQSEvent event = new SQSEvent();
    event.setRecords(List.of(sqsMessage(messageId, "test-bucket", "inputs/kb-123/audio.mp3")));

    when(mockExecutorService.<StartTranscriptionJobResponse>submit(any(Callable.class)))
        .thenReturn(executionExceptionFuture("Transcribe service error"));

    SQSBatchResponse response = handler.handleRequest(event, mockContext);

    assertNotNull(response);
    assertEquals(1, response.getBatchItemFailures().size());
    assertEquals(messageId, response.getBatchItemFailures().get(0).getItemIdentifier());
  }

  @Test
  @DisplayName("InterruptedException wraps in RuntimeException and sets interrupt flag")
  public void testInterruptedExceptionThrowsRuntimeException() {
    SQSEvent event = new SQSEvent();
    event.setRecords(
        List.of(sqsMessage("msg-interrupt", "test-bucket", "inputs/kb-123/audio.mp3")));

    when(mockExecutorService.<StartTranscriptionJobResponse>submit(any(Callable.class)))
        .thenReturn(interruptedFuture());

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, mockContext));

    assertInstanceOf(InterruptedException.class, ex.getCause());
    Thread.interrupted(); // clear interrupt flag so it doesn't leak between tests
  }

  @Test
  @DisplayName("Mixed batch: successful message not reported, failed message reported as failure")
  public void testMixedBatch() {
    SQSEvent event = new SQSEvent();
    event.setRecords(
        List.of(
            sqsMessage("msg-success", "test-bucket", "inputs/kb-123/audio1.mp3"),
            sqsMessage("msg-failure", "test-bucket", "inputs/kb-456/audio2.mp3")));

    when(mockExecutorService.<StartTranscriptionJobResponse>submit(any(Callable.class)))
        .thenReturn(successFuture())
        .thenReturn(executionExceptionFuture("Service error"));

    SQSBatchResponse response = handler.handleRequest(event, mockContext);

    assertNotNull(response);
    assertEquals(1, response.getBatchItemFailures().size());
    assertEquals("msg-failure", response.getBatchItemFailures().get(0).getItemIdentifier());
  }
}
