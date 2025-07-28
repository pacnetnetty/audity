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

module "data" {
  source   = "./modules/data"
  env_name = var.env_name
  app_name = var.app_name
}
