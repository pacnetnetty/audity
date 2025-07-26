output "spring_boot_service_url" {
  value = aws_api_gateway_deployment.spring_boot_service.invoke_url
}

output "lambda_function_arn" {
  value = aws_lambda_function.my_lambda_function.arn
}

output "electron_app_url" {
  value = "http://localhost:3000"
}