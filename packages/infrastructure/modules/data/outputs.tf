output "transcription_bucket_name" {
  description = "The name of the transcription S3 bucket."
  value       = aws_s3_bucket.transcription_bucket.bucket
}
