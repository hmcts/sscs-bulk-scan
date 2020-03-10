provider "azurerm" {
  version = "1.22.1"
}

data "azurerm_key_vault" "sscs_bulk_scan_key_vault" {
  name                = "${local.azureVaultName}"
  resource_group_name = "${local.azureVaultName}"
}

locals {
  azureVaultName = "sscs-bulk-scan-${var.env}"
}
