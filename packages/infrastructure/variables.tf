variable "aws_region" {
  description = "AWS region to deploy resources in."
  type        = string
  default     = "us-east-1"
}

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
