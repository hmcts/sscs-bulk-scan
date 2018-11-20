output "microserviceName" {
  value = "${var.component}"
}

output "vaultUri" {
  value = "${module.sscs-bulk-scan-vault.key_vault_uri}"
}

output "vaultName" {
  value = "${local.vaultName}"
}

output "idam-s2s-api" {
  value = "${local.s2s_url}"
}
