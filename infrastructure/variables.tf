variable "product" {
  type = "string"
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

variable "subscription" {}

variable "capacity" {
  default = "1"
}

variable "common_tags" {
  type = "map"
}

variable "idam_s2s_auth_microservice" {
  default = "ccd_data"
}

variable "idam_oauth2_client_id" {
  default = "sscs"
}
