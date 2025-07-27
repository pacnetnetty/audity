terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Example module usage (uncomment and configure as needed)
# module "network" {
#   source = "./modules/network"
#   # ...module variables...
# }
# module "data_storage" {
#   source = "./modules/data-storage"
#   # ...module variables...
# }
# module "api" {
#   source = "./modules/api"
#   # ...module variables...
# }
# module "frontend" {
#   source = "./modules/frontend"
#   # ...module variables...
# }
