package com.mina.agpeya

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Tasks
import com.mina.agpeya.data.DriveSettingsBackup
import com.mina.agpeya.data.DriveHttpException
import com.mina.agpeya.data.PrayerSettingsRepository
import com.mina.agpeya.data.SettingsBackup
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Keep
class DriveBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (inputData.getBoolean("recovery", false)) {
            return withContext(Dispatchers.IO) {
                enqueue(applicationContext).result.get()
                Result.success()
            }
        }
        val repository = PrayerSettingsRepository(applicationContext)
        var account: String? = null
        try {
            val snapshot = repository.backupSnapshot()
            account = snapshot.account ?: return Result.success()
            if (snapshot.revision == snapshot.uploadedRevision) return Result.success()
            repository.recordBackupStatus(account, "جارٍ نسخ الإعدادات إلى Google Drive...")
            val authorization = withContext(Dispatchers.IO) {
                Tasks.await(
                    Identity.getAuthorizationClient(applicationContext).authorize(
                        AuthorizationRequest.builder()
                            .setAccount(Account(account, "com.google"))
                            .setRequestedScopes(listOf(Scope(DriveSettingsBackup.SCOPE)))
                            .build(),
                    ),
                    30, TimeUnit.SECONDS,
                )
            }
            val token = authorization.accessToken
            if (authorization.hasResolution() || token.isNullOrBlank()) {
                repository.recordBackupStatus(account, "يلزم إعادة ربط حساب Google. افتح الإعدادات واختر الحساب مجدداً.")
                return Result.failure()
            }
            if (repository.backupSnapshot().account != account) return Result.success()
            DriveSettingsBackup().upload(token, SettingsBackup.encode(snapshot.settings))
            repository.recordBackupStatus(account, "تم نسخ آخر إعدادات محفوظة إلى Google Drive.", snapshot.revision)
            return Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("DriveBackupWorker", "Automatic settings backup failed", error)
            val cause = if (error is java.util.concurrent.ExecutionException) error.cause ?: error else error
            val retry = (cause is IOException && (cause !is DriveHttpException ||
                cause.status == 429 || cause.status >= 500)) ||
                cause is java.util.concurrent.TimeoutException ||
                (cause is ApiException && cause.statusCode in setOf(
                    CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR,
                ))
            account?.let {
                repository.recordBackupStatus(it, if (retry) {
                    "تعذر النسخ الآن. ستتم إعادة المحاولة تلقائياً."
                } else {
                    "تعذر النسخ التلقائي. تحقق من حساب Google أو أعد ربطه من الإعدادات."
                })
            }
            return if (retry) {
                Result.retry()
            } else Result.failure()
        }
    }

    companion object {
        private const val IMMEDIATE = "drive-settings-backup"
        private const val RECOVERY = "drive-settings-backup-recovery"
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        suspend fun schedule(context: Context) = withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(
                RECOVERY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DriveBackupWorker>(15, TimeUnit.MINUTES)
                    .setInputData(workDataOf("recovery" to true))
                    .setConstraints(constraints).build(),
            ).result.get()
            enqueue(context).result.get()
        }

        private fun enqueue(context: Context) =
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<DriveBackupWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )

        suspend fun cancel(context: Context) = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(IMMEDIATE).result.get()
                cancelUniqueWork(RECOVERY).result.get()
            }
        }
    }
}
