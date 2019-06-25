variable "product" {
  type = "string"
}

variable "raw_product" {
  default = "sscs-bulk-scan" // jenkins-library overrides product for PRs and adds e.g. pr-118-rpe-...
}

variable "component" {
  type = "string"
}

variable "location_app" {
  type    = "string"
  default = "UK South"
}

variable "env" {
  type = "string"
}

variable "ilbIp" {}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  type        = "string"
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "subscription" {}

variable "capacity" {
  default = "1"
}

variable "common_tags" {
  type = "map"
}

variable "infrastructure_env" {
  default     = "test"
  description = "Infrastructure environment to point to"
}

variable "idam_s2s_auth_microservice" {
  default = "sscs_bulkscan"
}

variable "idam_oauth2_client_id" {
  default = "sscs"
}

variable "idam_redirect_url" {
  default = "https://evidence-sharing-preprod.sscs.reform.hmcts.net"
}

variable "idam_url" {
  default = "http://testing.test"
}

variable "robotics_email_subject" {
  type    = "string"
  default = "Robotics Data"
}

variable "robotics_email_message" {
  type    = "string"
  default = "Please find attached the robotics json file \nPlease do not respond to this email"
}

variable "appeal_email_smtp_tls_enabled" {
  type    = "string"
  default = "true"
}

variable "appeal_email_smtp_ssl_trust" {
  type    = "string"
  default = "*"
}

variable "robotics_enabled" {
  type    = "string"
  default = "false"
}

variable "send_to_dwp_enabled" {
  type    = "string"
  default = "false"
}

variable "appinsights_instrumentation_key" {
  description = "Instrumentation key of the App Insights instance this webapp should use. Module will create own App Insights resource if this is not provided"
  default     = ""
}

variable "feature_rpc_email_robotics" {
  default = "true"
}
