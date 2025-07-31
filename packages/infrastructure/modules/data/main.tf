resource "aws_s3_bucket" "transcription_bucket" {
  bucket = "${var.app_name}-${var.env_name}-transcriptions"
}
resource "aws_s3_bucket_public_access_block" "transcription_bucket_bpa" {
  bucket = aws_s3_bucket.transcription_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_policy" "transcription_bucket_policy" {
  bucket = aws_s3_bucket.transcription_bucket.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          "${aws_s3_bucket.transcription_bucket.arn}/*",
          aws_s3_bucket.transcription_bucket.arn
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}


resource "aws_sqs_queue" "transcription_input_dlq" {
  name                       = "${var.app_name}-${var.env_name}-transcription-input-dlq"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 604800 # 7 days
  sqs_managed_sse_enabled    = true
}
resource "aws_sqs_queue_policy" "transcription_input_dlq_policy" {
  queue_url = aws_sqs_queue.transcription_input_dlq.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Deny"
        Principal = "*"
        Action    = "sqs:*"
        Resource  = aws_sqs_queue.transcription_input_dlq.arn
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}
resource "aws_sqs_queue" "transcription_input_queue" {
  name                       = "${var.app_name}-${var.env_name}-transcription-input"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 14400 # 4 hrs
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transcription_input_dlq.arn
    maxReceiveCount     = 2
  })
}
resource "aws_sqs_queue_policy" "transcription_input_queue_policy" {
  queue_url = aws_sqs_queue.transcription_input_queue.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = "*"
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.transcription_input_queue.arn
        Condition = {
          ArnLike = {
            "aws:SourceArn" = aws_s3_bucket.transcription_bucket.arn
          }
        }
      },
      {
        Effect    = "Deny"
        Principal = "*"
        Action    = "sqs:*"
        Resource  = aws_sqs_queue.transcription_input_queue.arn
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}
resource "aws_s3_bucket_notification" "transcription_bucket_notification" {
  bucket = aws_s3_bucket.transcription_bucket.id
  queue {
    events        = ["s3:ObjectCreated:*"]
    filter_prefix = "input/"
    queue_arn     = aws_sqs_queue.transcription_input_queue.arn
  }
}

