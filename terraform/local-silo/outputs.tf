output "bucket_name" {
  description = "Nombre del bucket S3 de DataFlow."
  value       = aws_s3_bucket.dataflow_files.bucket
}

output "bucket_arn" {
  description = "ARN del bucket S3 de DataFlow."
  value       = aws_s3_bucket.dataflow_files.arn
}
