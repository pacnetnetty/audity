resource "aws_s3_bucket" "audity_bucket" {
  bucket = "audity-bucket"
  acl    = "private"
}

resource "aws_lambda_function" "audity_lambda" {
  function_name = "audityLambda"
  handler       = "com.audity.lambda.Handler::handleRequest"
  runtime       = "java11"
  role          = aws_iam_role.lambda_exec.arn
  s3_bucket     = aws_s3_bucket.audity_bucket.bucket
  s3_key        = "path/to/your/lambda/package.zip" # Update with the actual path to your Lambda package
}

resource "aws_iam_role" "lambda_exec" {
  name = "lambda_exec_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Effect    = "Allow"
        Sid       = ""
      },
    ]
  })
}

resource "aws_iam_policy_attachment" "lambda_policy" {
  name       = "lambda_policy_attachment"
  roles      = [aws_iam_role.lambda_exec.name]
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}