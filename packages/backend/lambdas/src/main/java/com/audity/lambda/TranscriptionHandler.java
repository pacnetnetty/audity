package com.audity.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import com.audity.lambda.util.LambdaResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotificationRecord;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribe.TranscribeAsyncClient;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobResponse;

public class TranscriptionHandler implements RequestHandler<SQSEvent, LambdaResponse> {

  private static final TranscribeAsyncClient transcribeClient =
      TranscribeAsyncClient.builder()
          .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
          .build();

  @Override
  public LambdaResponse handleRequest(SQSEvent event, Context context) {
    List<SQSMessage> messages = event.getRecords();
    context.getLogger().log("Number of messages: " + messages.size(), LogLevel.DEBUG);

    List<CompletableFuture<StartTranscriptionJobResponse>> futures =
        messages.stream()
            .map(SQSMessage::getBody)
            .map(S3EventNotification::fromJson)
            .flatMap(notification -> notification.getRecords().stream())
            .map(record -> startTranscribeJobForRecord(record, context))
            .toList();

    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

    context.getLogger().log("All Transcribe jobs started successfully", LogLevel.INFO);
    return new LambdaResponse(200, "All Transcribe jobs started successfully");
  }

  private CompletableFuture<StartTranscriptionJobResponse> startTranscribeJobForRecord(
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

    return transcribeClient
        .startTranscriptionJob(request)
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                context
                    .getLogger()
                    .log(
                        "Failed to start Transcribe job for "
                            + mediaUri
                            + ": "
                            + error.getMessage(),
                        LogLevel.ERROR);

                throw new RuntimeException(error);
              } else {
                context
                    .getLogger()
                    .log(
                        "Started Transcribe job: "
                            + response.transcriptionJob().transcriptionJobName(),
                        LogLevel.INFO);
              }
            });
  }
}
