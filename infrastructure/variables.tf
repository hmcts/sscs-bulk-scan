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
  default = "ccd_data"
}

variable "idam_oauth2_client_id" {
  default = "bsp"
}

variable "idam_redirect_url" {
  default = "https://sscs-case-loader-sandbox.service.core-compute-sandbox.internal"
}

variable "idam_url" {
  default = "http://testing.test"
}
