package com.audity.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotificationRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;

public class TranscriptionHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  private static final TranscribeClient transcribeClient =
      TranscribeClient.builder()
          .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
          .build();

  private static final ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();

  @Override
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    List<SQSMessage> messages = event.getRecords();
    context.getLogger().log("Number of messages: " + messages.size(), LogLevel.DEBUG);

    // saving message IDs to allow for reporting partial failures in this batch of messages
    var messageIdToFuture = new HashMap<String, Future<StartTranscriptionJobResponse>>();
    for (var message : messages) {
      Future<StartTranscriptionJobResponse> future =
          S3EventNotification.fromJson(message.getBody()).getRecords().stream()
              .map(record -> startTranscribeJobForRecord(record, context))
              .findFirst()
              .orElseThrow();

      messageIdToFuture.put(message.getMessageId(), future);
    }

    var batchItemFailures = new ArrayList<SQSBatchResponse.BatchItemFailure>();
    for (Map.Entry<String, Future<StartTranscriptionJobResponse>> entry :
        messageIdToFuture.entrySet()) {
      try {
        entry.getValue().get();
      } catch (ExecutionException e) {
        context.getLogger().log("Error starting Transcribe job: " + e.getMessage(), LogLevel.ERROR);
        batchItemFailures.add(new SQSBatchResponse.BatchItemFailure(entry.getKey()));
      } catch (InterruptedException e) {
        context.getLogger().log("Thread interrupt: " + e.getMessage(), LogLevel.ERROR);
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    return new SQSBatchResponse(batchItemFailures);
  }

  private Future<StartTranscriptionJobResponse> startTranscribeJobForRecord(
      S3EventNotificationRecord record, Context context) {

    String bucketName = record.getS3().getBucket().getName();
    String objectKey = record.getS3().getObject().getKey();
    String mediaUri = "s3://" + bucketName + "/" + objectKey;
    String kbId =
        objectKey.split("/")[1]; // Can assume expected format "input/<KB_ID>/<OBJECT_FILENAME>"
    String jobName =
        "transcription-"
            + System.currentTimeMillis()
            + "-"
            + objectKey.replaceAll("[^a-zA-Z0-9_-]", "_");

    context
        .getLogger()
        .log("Starting Transcribe job: " + jobName + " for " + mediaUri, LogLevel.INFO);

    // TODO: add multi-speaker detection
    var request =
        StartTranscriptionJobRequest.builder()
            .transcriptionJobName(jobName)
            .languageCode(LanguageCode.EN_US) // TODO: can add flexibility for this later
            .media(Media.builder().mediaFileUri(mediaUri).build())
            .outputBucketName(bucketName)
            .outputKey("output/" + kbId + "/" + jobName + ".json")
            .build();

    return vtExecutor.submit(
        () -> {
          StartTranscriptionJobResponse response = transcribeClient.startTranscriptionJob(request);
          context
              .getLogger()
              .log(
                  "Started Transcribe job: " + response.transcriptionJob().transcriptionJobName(),
                  LogLevel.INFO);
          return response;
        });
  }
}
