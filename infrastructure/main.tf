provider "azurerm" {
  features {}
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location_app
}

data "azurerm_user_assigned_identity" "sscs-identity" {
  name                = "sscs-${var.env}-mi"
  resource_group_name = "managed-identities-${var.env}-rg"
}

locals {
  vaultName = "sscs-bulk-scan-${var.env}"
  sscsRg   = "sscs-${var.env}"
}

module "sscs-bulk-scan-vault" {
  source                  = "git@github.com:hmcts/cnp-module-key-vault?ref=azurermv2"
  name                    = local.vaultName
  product                 = var.product
  env                     = var.env
  tenant_id               = var.tenant_id
  object_id               = var.jenkins_AAD_objectId
  resource_group_name     = azurerm_resource_group.rg.name
  product_group_object_id = "70de400b-4f47-4f25-a4f0-45e1ee4e4ae3"
  common_tags             = var.common_tags
  managed_identity_object_ids = ["${data.azurerm_user_assigned_identity.sscs-identity.principal_id}"]
}

resource "azurerm_application_insights" "appinsights" {
  name                = "${var.product}-${var.env}"
  location            = var.location_app
  resource_group_name = azurerm_resource_group.rg.name
  application_type    = var.appinsights_application_type
}

data "azurerm_key_vault" "sscs_key_vault" {
  name                = "sscs-${var.env}"
  resource_group_name = local.sscsRg
}

data "azurerm_key_vault_secret" "app_insights_key" {
  name         = "AppInsightsInstrumentationKey"
  value        = azurerm_application_insights.appinsights.instrumentation_key
  key_vault_id = data.azurerm_key_vault.sscs_key_vault.id
}

output "appInsightsInstrumentationKey" {
  value = azurerm_application_insights.appinsights.instrumentation_key
}




