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

output "robotics_email_from" {
  value ="${data.azurerm_key_vault_secret.robotics_email_from.value}"
}

output "robotics_email_to" {
  value = "${data.azurerm_key_vault_secret.robotics_email_to.value}"
}

output "robotics_email_subject" {
  value = "${var.robotics_email_subject}"
}

output "robotics_email_message" {
  value = "${var.robotics_email_message}"
}

output "smtp_host" {
  value = "${data.azurerm_key_vault_secret.smtp_host.value}"
}

output "smtp_port" {
  value = "${data.azurerm_key_vault_secret.smtp_port.value}"
}

output "appeal_email_smtp_tls_enabled" {
  value = "${var.appeal_email_smtp_tls_enabled}"
}

output "appeal_email_smtp_ssl_trust" {
  value = "${var.appeal_email_smtp_ssl_trust}"
}
