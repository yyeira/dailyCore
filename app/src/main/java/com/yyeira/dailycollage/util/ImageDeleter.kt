package com.yyeira.dailycollage.util

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

class ImageDeleter(
    private val contentResolver: ContentResolver,
) {
    sealed class DeleteResult {
        data class Success(val deletedCount: Int) : DeleteResult()
        data class NeedsUserConfirmation(val intentSender: android.content.IntentSender) : DeleteResult()
        data class Failed(val message: String) : DeleteResult()
    }

    fun delete(uris: List<Uri>): DeleteResult {
        if (uris.isEmpty()) return DeleteResult.Success(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                DeleteResult.NeedsUserConfirmation(pendingIntent.intentSender)
            } catch (e: Exception) {
                DeleteResult.Failed(e.message ?: "删除请求失败")
            }
        }

        var deletedCount = 0
        for (uri in uris) {
            try {
                if (contentResolver.delete(uri, null, null) > 0) {
                    deletedCount++
                }
            } catch (e: RecoverableSecurityException) {
                return DeleteResult.NeedsUserConfirmation(e.userAction.actionIntent.intentSender)
            } catch (e: Exception) {
                return DeleteResult.Failed(e.message ?: "删除失败")
            }
        }
        return DeleteResult.Success(deletedCount)
    }
}
