terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.35.1"
    }
  }

  backend "s3" {
    bucket = "audity-tf-state-pacnetnetty"
    key    = "audity-tf-state/dev/terraform.tfstate"
    region = "us-west-2"
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

module "compute" {
  source                        = "./modules/compute"
  env_name                      = var.env_name
  app_name                      = var.app_name
  transcription_input_queue_arn = module.data.transcription_input_queue_arn
  transcription_bucket_arn      = module.data.transcription_bucket_arn
}
