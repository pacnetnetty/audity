variable "env_name" {
  description = "Environment name (e.g., dev, prod)."
  type        = string
  default     = "dev"
}

variable "app_name" {
  description = "Name of the application."
  type        = string
  default     = "audity"
}

variable "transcription_input_queue_arn" {
  description = "ARN of the SQS transcription input queue."
  type        = string
}

variable "transcription_bucket_arn" {
  description = "ARN of the transcription input/output bucket."
  type        = string
}
