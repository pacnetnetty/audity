variable "aws_region" {
  description = "The AWS region where the resources will be deployed"
  type        = string
  default     = "us-east-1"
}

variable "lambda_function_name" {
  description = "The name of the AWS Lambda function"
  type        = string
  default     = "audityLambdaFunction"
}

variable "spring_boot_service_name" {
  description = "The name of the Spring Boot service"
  type        = string
  default     = "auditySpringBootService"
}

variable "db_instance_type" {
  description = "The instance type for the database"
  type        = string
  default     = "db.t2.micro"
}

variable "db_name" {
  description = "The name of the database"
  type        = string
  default     = "audityDB"
}

variable "db_username" {
  description = "The username for the database"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = "The password for the database"
  type        = string
  sensitive   = true
}