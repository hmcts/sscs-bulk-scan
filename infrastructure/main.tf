provider "azurerm" {
  version = "1.19.0"
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name  = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  local_env = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "aat" : "saat" : var.env}"

  s2s_url   = "http://rpe-service-auth-provider-${local.local_env}.service.core-compute-${local.local_env}.internal"
  ccdApiUrl = "http://ccd-data-store-api-${local.local_env}.service.core-compute-${local.local_env}.internal"
  documentStore = "http://dm-store-${local.local_env}.service.core-compute-${local.local_env}.internal"

  vaultName = "sscs-bulk-scan-${local.local_env}"

  # URI of vault that stores long-term secrets. It's the app's own Key Vault, except for (s)preview,
  # where vaults are short-lived and can only store secrets generated during deployment
  preview_vault_name = "https://${var.raw_product}-aat.vault.azure.net/"
  permanent_vault_uri = "${var.env != "preview" ? module.sscs-bulk-scan-vault.key_vault_uri : local.preview_vault_name}"
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
    LOGBACK_REQUIRE_ALERT_LEVEL = "false"
    LOGBACK_REQUIRE_ERROR_CODE  = "false"

    S2S_SECRET                 = "${data.azurerm_key_vault_secret.sscs_s2s_secret.value}"
    IDAM_S2S_AUTH              = "${local.s2s_url}"
    IDAM_S2S_AUTH_MICROSERVICE = "${var.idam_s2s_auth_microservice}"

    IDAM_URL = "${var.idam_url}"

    IDAM_OAUTH2_USER_EMAIL    = "${data.azurerm_key_vault_secret.idam_sscs_systemupdate_user.value}"
    IDAM_OAUTH2_USER_PASSWORD = "${data.azurerm_key_vault_secret.idam_sscs_systemupdate_password.value}"
    IDAM_OAUTH2_CLIENT_SECRET = "${data.azurerm_key_vault_secret.idam_sscs_oauth2_client_secret.value}"
    IDAM_OAUTH2_CLIENT_ID     = "${var.idam_oauth2_client_id}"
    IDAM_OAUTH2_REDIRECT_URL  = "${var.idam_redirect_url}"

    CORE_CASE_DATA_API_URL = "${local.ccdApiUrl}"

    ROBOTICS_EMAIL_FROM    = "${data.azurerm_key_vault_secret.robotics_email_from.value}"
    ROBOTICS_EMAIL_TO      = "${data.azurerm_key_vault_secret.robotics_email_to.value}"
    ROBOTICS_EMAIL_SUBJECT = "${var.robotics_email_subject}"
    ROBOTICS_EMAIL_MESSAGE = "${var.robotics_email_message}"
    ROBOTICS_ENABLED       = "${var.robotics_enabled}"

    EMAIL_SERVER_HOST      = "${data.azurerm_key_vault_secret.smtp_host.value}"
    EMAIL_SERVER_PORT      = "${data.azurerm_key_vault_secret.smtp_port.value}"
    EMAIL_SMTP_TLS_ENABLED = "${var.appeal_email_smtp_tls_enabled}"
    EMAIL_SMTP_SSL_TRUST   = "${var.appeal_email_smtp_ssl_trust}"

    DOCUMENT_MANAGEMENT_URL = "${local.documentStore}"
  }
}

module "sscs-bulk-scan-vault" {
  source                  = "git@github.com:hmcts/moj-module-key-vault?ref=master"
  name                    = "${local.vaultName}"
  product                 = "${var.product}"
  env                     = "${var.env}"
  tenant_id               = "${var.tenant_id}"
  object_id               = "${var.jenkins_AAD_objectId}"
  resource_group_name     = "${azurerm_resource_group.rg.name}"
  product_group_object_id = "70de400b-4f47-4f25-a4f0-45e1ee4e4ae3"
}

data "azurerm_key_vault_secret" "sscs_s2s_secret" {
  name      = "sscs-s2s-secret"
  vault_uri = "${local.permanent_vault_uri}"
}

resource "azurerm_key_vault_secret" "test_sscs_s2s_secret" {
  name      = "test-sscs-s2s-secret"
  value     = "${data.azurerm_key_vault_secret.sscs_s2s_secret.value}"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_systemupdate_user" {
  name      = "idam-sscs-systemupdate-user"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "test_idam_sscs_systemupdate_user" {
  name      = "test-idam-sscs-systemupdate-user"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_systemupdate_password" {
  name      = "idam-sscs-systemupdate-password"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "test_idam_sscs_systemupdate_password" {
  name      = "test-idam-sscs-systemupdate-password"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_oauth2_client_secret" {
  name      = "idam-sscs-oauth2-client-secret"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "test_idam_sscs_oauth2_client_secret" {
  name      = "test-idam-sscs-oauth2-client-secret"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "robotics_email_from" {
  name      = "robotics-email-from"
  vault_uri = "${local.permanent_vault_uri}"
}

resource "azurerm_key_vault_secret" "test_robotics_email_from" {
  name      = "test-robotics-email-from"
  value     = "${data.azurerm_key_vault_secret.robotics_email_from.value}"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "robotics_email_to" {
  name      = "robotics-email-to"
  vault_uri = "${local.permanent_vault_uri}"
}

resource "azurerm_key_vault_secret" "test_robotics_email_to" {
  name      = "test-robotics-email-to"
  value     = "${data.azurerm_key_vault_secret.robotics_email_to.value}"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "smtp_host" {
  name      = "smtp-host"
  vault_uri = "${local.permanent_vault_uri}"
}

resource "azurerm_key_vault_secret" "test_smtp_host" {
  name      = "test-smtp-host"
  value     = "${data.azurerm_key_vault_secret.smtp_host.value}"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

data "azurerm_key_vault_secret" "smtp_port" {
  name      = "smtp-port"
  vault_uri = "${local.permanent_vault_uri}"
}

resource "azurerm_key_vault_secret" "test_smtp_port" {
  name      = "test-smtp-port"
  value     = "${data.azurerm_key_vault_secret.smtp_port.value}"
  vault_uri = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}
