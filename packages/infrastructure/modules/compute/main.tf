locals {
  lambdas_jar_path = "${path.root}/../backend/lambdas/target/lambdas.jar"
}

resource "aws_iam_role" "transcription_lambda_role" {
  name = "${var.app_name}-${var.env_name}-transcription-lambda-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}
resource "aws_iam_role_policy" "transcription_lambda_policy" {
  name = "${var.app_name}-${var.env_name}-transcription-lambda-policy"
  role = aws_iam_role.transcription_lambda_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = var.transcription_input_queue_arn
      },
      {
        Effect = "Allow"
        Action = [
          "transcribe:StartTranscriptionJob",
          "transcribe:GetTranscriptionJob"
        ]
        Resource = "*"
      }
    ]
  })
}
resource "aws_lambda_function" "transcription_handler" {
  function_name    = "${var.app_name}-${var.env_name}-transcription-handler"
  role             = aws_iam_role.transcription_lambda_role.arn
  handler          = "com.audity.lambda.TranscriptionHandler::handleRequest"
  runtime          = "java17"
  filename         = local.lambdas_jar_path
  source_code_hash = filebase64sha256(local.lambdas_jar_path)
  memory_size      = 256
  timeout          = 30
  environment {
    variables = {
      TRANSCRIPTION_BUCKET_NAME = var.transcription_bucket_name
    }
  }
}
resource "aws_lambda_event_source_mapping" "transcription_input_queue_mapping" {
  event_source_arn = var.transcription_input_queue_arn
  function_name    = aws_lambda_function.transcription_handler.arn
  batch_size       = 10
  enabled          = true
  scaling_config {
    maximum_concurrency = 2
  }
}

