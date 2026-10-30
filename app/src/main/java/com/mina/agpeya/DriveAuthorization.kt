package com.mina.agpeya

import android.app.Activity
import android.accounts.AccountManager
import com.google.android.gms.common.AccountPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
internal fun DriveAuthorization(viewModel: AgpeyaViewModel) {
    val pendingIntent by viewModel.syncConsent.collectAsState()
    val chooseAccount by viewModel.chooseDriveAccount.collectAsState()
    var awaitingAccount by rememberSaveable { mutableStateOf(false) }
    val accountPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        awaitingAccount = false
        viewModel.selectDriveAccount(
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            } else null,
        )
    }
    LaunchedEffect(chooseAccount) {
        if (!chooseAccount) {
            awaitingAccount = false
        } else if (!awaitingAccount) {
            try {
                awaitingAccount = true
                accountPicker.launch(AccountPicker.newChooseAccountIntent(
                    AccountPicker.AccountChooserOptions.Builder()
                        .setAllowableAccountsTypes(listOf("com.google"))
                        .setAlwaysShowAccountPicker(true)
                        .build(),
                ))
            } catch (error: Exception) {
                awaitingAccount = false
                viewModel.settingsSyncAuthorizationFailed(error)
            }
        }
    }
    var awaitingConsent by rememberSaveable { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        awaitingConsent = false
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.settingsSyncAuthorizationFailed(null)
        } else {
            viewModel.completeDriveConsent(result.data)
        }
    }
    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent
        if (intent == null) {
            awaitingConsent = false
        } else if (!awaitingConsent) {
            try {
                awaitingConsent = true
                consent.launch(IntentSenderRequest.Builder(intent).build())
            } catch (error: Exception) {
                awaitingConsent = false
                viewModel.settingsSyncAuthorizationFailed(error)
            }
        }
    }
}
