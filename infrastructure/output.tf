output "microserviceName" {
  value = "${var.component}"
}

output "vaultUri" {
  value = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

output "vaultName" {
  value = "${local.vaultName}"
}

output "idam_s2s_auth" {
  value = "${local.s2s_url}"
}

output "idam_url" {
  value = "${var.idam_url}"
}

output "idam_oauth2_redirect_url" {
  value = "${var.idam_redirect_url}"
}

output "idam_oauth2_client_secret" {
  value = "${data.azurerm_key_vault_secret.idam_sscs_oauth2_client_secret.value}"
}
