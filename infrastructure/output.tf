output "microserviceName" {
  value = "${var.component}"
}

output "vaultUri" {
  value = "${data.azurerm_key_vault.sscs_bulk_scan_key_vault.vault_uri}"
}
