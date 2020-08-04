provider "azurerm" {
  version = "1.44.0"
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

data "azurerm_user_assigned_identity" "sscs-identity" {
  name                = "sscs-${var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

locals {
  vaultName = "sscs-bulk-scan-${var.env}"
  preview_vault_name = "https://${var.raw_product}-aat.vault.azure.net/"
  permanent_vault_uri = "${var.env != "preview" ? module.sscs-bulk-scan-vault.key_vault_uri : local.preview_vault_name}"
}

module "sscs-bulk-scan-vault" {
  source                  = "git@github.com:hmcts/cnp-module-key-vault?ref=master"
  name                    = "${local.vaultName}"
  product                 = "${var.product}"
  env                     = "${var.env}"
  tenant_id               = "${var.tenant_id}"
  object_id               = "${var.jenkins_AAD_objectId}"
  resource_group_name     = "${azurerm_resource_group.rg.name}"
  product_group_object_id = "70de400b-4f47-4f25-a4f0-45e1ee4e4ae3"
  common_tags             = "${var.common_tags}"
  managed_identity_object_ids = ["${data.azurerm_user_assigned_identity.sscs-identity.principal_id}"]
}

data "azurerm_key_vault_secret" "sscs_s2s_secret" {
  name      = "sscs-s2s-secret"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_systemupdate_user" {
  name      = "idam-sscs-systemupdate-user"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_systemupdate_password" {
  name      = "idam-sscs-systemupdate-password"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "idam_sscs_oauth2_client_secret" {
  name      = "idam-sscs-oauth2-client-secret"
  vault_uri = "${local.permanent_vault_uri}"
}


data "azurerm_key_vault_secret" "appinsights_instrumentation_key" {
  name      = "AppInsightsInstrumentationKey"
  vault_uri = "${local.permanent_vault_uri}"
}
