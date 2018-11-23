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

output "idam_oauth2_user_email" {
  value = "${data.azurerm_key_vault_secret.idam_sscs_systemupdate_user.value}"
}

output "idam_oauth2_user_password" {
  value = "${data.azurerm_key_vault_secret.idam_sscs_systemupdate_password.value}"
}

output "idam_oauth2_client_id" {
  value = "${var.idam_oauth2_client_id}"
}
