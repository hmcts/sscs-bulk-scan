provider "azurerm" {}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

data "azurerm_key_vault_secret" "sscs-s2s-secret" {
  name = "sscs-s2s-secret"
  vault_uri = "${data.azurerm_key_vault.sscs_key_vault.vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-systemupdate-user" {
  name = "idam-sscs-systemupdate-user"
  vault_uri = "${data.azurerm_key_vault.sscs_key_vault.vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-systemupdate-password" {
  name = "idam-sscs-systemupdate-password"
  vault_uri = "${data.azurerm_key_vault.sscs_key_vault.vault_uri}"
}

data "azurerm_key_vault_secret" "idam-sscs-oauth2-client-secret" {
  name = "idam-sscs-oauth2-client-secret"
  vault_uri = "${data.azurerm_key_vault.sscs_key_vault.vault_uri}"
}

data "azurerm_key_vault_secret" "idam-api" {
  name = "idam-api"
  vault_uri = "${data.azurerm_key_vault.sscs_key_vault.vault_uri}"
}

locals {
  ase_name  = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  local_env = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "aat" : "saat" : var.env}"
  s2s_url   = "http://rpe-service-auth-provider-${local.local_env}.service.core-compute-${local.local_env}.internal"

  ccdApiUrl = "http://ccd-data-store-api-${local.local_env}.service.core-compute-${local.local_env}.internal"
}

module "sscs-bulk-scan" {
  source              = "git@github.com:hmcts/moj-module-webapp?ref=master"
  product             = "${var.product}-${var.component}"
  location            = "${var.location_app}"
  env                 = "${var.env}"
  ilbIp               = "${var.ilbIp}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  subscription        = "${var.subscription}"
  capacity            = "${var.capacity}"
  common_tags         = "${var.common_tags}"

  app_settings = {
    LOGBACK_REQUIRE_ALERT_LEVEL = false
    LOGBACK_REQUIRE_ERROR_CODE  = false

    IDAM_S2S-AUTH_URL      = "${local.s2s_url}"
    CORE_CASE_DATA_API_URL = "${local.ccdApiUrl}"

    IDAM_URL = "${data.azurerm_key_vault_secret.idam-api.value}"

    IDAM.S2S-AUTH.TOTP_SECRET  = "${data.azurerm_key_vault_secret.sscs-s2s-secret.value}"
    IDAM.S2S-AUTH              = "${local.s2sCnpUrl}"
    IDAM.S2S-AUTH.MICROSERVICE = "${var.idam_s2s_auth_microservice}"

    IDAM_SSCS_SYSTEMUPDATE_USER = "${data.azurerm_key_vault_secret.idam-sscs-systemupdate-user.value}"
    IDAM_SSCS_SYSTEMUPDATE_PASSWORD = "${data.azurerm_key_vault_secret.idam-sscs-systemupdate-password.value}"

    IDAM_OAUTH2_CLIENT_ID     = "${var.idam_oauth2_client_id}"
    IDAM_OAUTH2_CLIENT_SECRET = "${data.azurerm_key_vault_secret.idam-sscs-oauth2-client-secret.value}"
    IDAM_OAUTH2_REDIRECT_URL  = "${var.idam_redirect_url}"

  }
}
