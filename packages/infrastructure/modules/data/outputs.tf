output "transcription_bucket_arn" {
  description = "The ARN of the transcription S3 bucket."
  value       = aws_s3_bucket.transcription_bucket.arn
}

output "transcription_input_queue_arn" {
  description = "ARN of the SQS transcription input queue."
  value       = aws_sqs_queue.transcription_input_queue.arn
}
