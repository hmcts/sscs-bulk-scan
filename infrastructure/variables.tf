variable "product" {
}

variable "raw_product" {
  default = "sscs-bulk-scan" // jenkins-library overrides product for PRs and adds e.g. pr-118-rpe-...
}

variable "component" {
}

variable "location_app" {
  default = "UK South"
}

variable "env" {
}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "subscription" {}

variable "capacity" {
  default = "1"
}

variable "common_tags" {
  type = map(string)
}

variable "infrastructure_env" {
  default     = "test"
  description = "Infrastructure environment to point to"
}

variable "appinsights_instrumentation_key" {
  description = "Instrumentation key of the App Insights instance this webapp should use. Module will create own App Insights resource if this is not provided"
  default     = ""
}

variable "managed_identity_object_id" {
  default = ""
}

variable "appinsights_application_type" {
  default     = "web"
  description = "Type of Application Insights (Web/Other)"
}
