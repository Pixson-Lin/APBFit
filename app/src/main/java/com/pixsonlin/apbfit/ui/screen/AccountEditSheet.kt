package com.pixsonlin.apbfit.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixsonlin.apbfit.R
import com.pixsonlin.apbfit.ui.viewmodel.AccountEditItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditSheet(
    accounts: List<AccountEditItem>,
    enabledAccountCount: Int,
    onDismiss: () -> Unit,
    onToggleEnabled: (accountId: String, enabled: Boolean) -> Unit,
    onSignOut: (accountId: String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.account_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.account_edit_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            accounts.forEach { account ->
                AccountEditRow(
                    account = account,
                    canUncheck = !(account.isEnabled && enabledAccountCount <= 1),
                    onToggleEnabled = onToggleEnabled,
                    onSignOut = onSignOut,
                )
            }
            Button(
                onClick = onAddAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_google_account))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.account_edit_done))
            }
        }
    }
}

@Composable
private fun AccountEditRow(
    account: AccountEditItem,
    canUncheck: Boolean,
    onToggleEnabled: (accountId: String, enabled: Boolean) -> Unit,
    onSignOut: (accountId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = account.isEnabled,
            onCheckedChange = { checked ->
                if (!checked && !canUncheck) return@Checkbox
                onToggleEnabled(account.id, checked)
            },
        )
        Text(
            text = account.email,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onSignOut(account.id) }) {
            Text(stringResource(R.string.account_edit_remove))
        }
    }
}
