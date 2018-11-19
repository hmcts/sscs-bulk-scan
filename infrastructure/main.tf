provider "azurerm" {}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name  = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  local_env = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "aat" : "saat" : var.env}"

  s2sCnpUrl   = "http://rpe-service-auth-provider-${local.local_env}.service.core-compute-${local.local_env}.internal"
  ccdApiUrl = "http://ccd-data-store-api-${local.local_env}.service.core-compute-${local.local_env}.internal"

  vaultName = "sscs-bulk-scan-${local.local_env}"

  permanent_vault_uri    = "https://${var.raw_product}-${local.local_env}.vault.azure.net/"
}

module "sscs-bulk-scan" {
  source = "git@github.com:hmcts/moj-module-webapp?ref=master"
  product = "${var.product}-${var.component}"
  location = "${var.location_app}"
  env = "${var.env}"
  ilbIp = "${var.ilbIp}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  subscription = "${var.subscription}"
  capacity = "${var.capacity}"
  common_tags = "${var.common_tags}"

  app_settings = {
    LOGBACK_REQUIRE_ALERT_LEVEL = false
    LOGBACK_REQUIRE_ERROR_CODE = false

    CORE_CASE_DATA_API_URL = "${local.ccdApiUrl}"

    IDAM_URL = "${data.azurerm_key_vault_secret.idam-api.value}"

    IDAM.S2S-AUTH.TOTP_SECRET = "${data.azurerm_key_vault_secret.sscs-s2s-secret.value}"
    IDAM.S2S-AUTH = "${local.s2sCnpUrl}"
    IDAM.S2S-AUTH.MICROSERVICE = "${var.idam_s2s_auth_microservice}"

    IDAM_SSCS_SYSTEMUPDATE_USER = "${data.azurerm_key_vault_secret.idam-sscs-systemupdate-user.value}"
    IDAM_SSCS_SYSTEMUPDATE_PASSWORD = "${data.azurerm_key_vault_secret.idam-sscs-systemupdate-password.value}"

    IDAM_OAUTH2_CLIENT_ID = "${var.idam_oauth2_client_id}"
    IDAM_OAUTH2_CLIENT_SECRET = "${data.azurerm_key_vault_secret.idam-sscs-oauth2-client-secret.value}"
    IDAM_OAUTH2_REDIRECT_URL = "${var.idam_redirect_url}"

  }
}

module "sscs-bulk-scan-vault" {
  source              = "git@github.com:contino/moj-module-key-vault?ref=master"
  name                = "${local.vaultName}"
  product             = "${var.product}"
  env                 = "${var.env}"
  tenant_id           = "${var.tenant_id}"
  object_id           = "${var.jenkins_AAD_objectId}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  product_group_object_id = "70de400b-4f47-4f25-a4f0-45e1ee4e4ae3"
}

data "azurerm_key_vault_secret" "sscs-s2s-secret" {
  name      = "sscs-s2s-secret"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-systemupdate-user" {
  name      = "idam-sscs-systemupdate-user"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-systemupdate-password" {
  name      = "idam-sscs-systemupdate-password"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-oauth2-client-secret" {
  name      = "idam-sscs-oauth2-client-secret"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "idam-api" {
  name      = "idam-api"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}
