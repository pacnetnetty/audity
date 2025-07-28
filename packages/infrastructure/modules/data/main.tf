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
