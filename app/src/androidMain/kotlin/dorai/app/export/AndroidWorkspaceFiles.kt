@file:Suppress("DEPRECATION")

package dorai.app.export

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dorai.app.components.FrontendFailure
import dorai.app.findings.PlanDocument
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
actual fun rememberWorkspaceFiles(): WorkspaceFiles {
    val context = LocalContext.current
    return remember(context) { AndroidWorkspaceFiles(context) }
}

class AndroidWorkspaceFiles(private val context: Context) : WorkspaceFiles {
    override suspend fun openPlan(): PlanDocument? {
        val uri = choose(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
        }) ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "plan.json"
            val input = resolver.openInputStream(uri) ?: throw FrontendFailure("The selected file could not be opened.")
            val bytes = input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > PlanDocument.MAX_BYTES) throw FrontendFailure("The plan must be no larger than 10 MB.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            PlanDocument(name, bytes)
        }
    }

    override suspend fun save(name: String, mediaType: String, bytes: ByteArray): SaveOutcome {
        val uri = choose(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mediaType
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, name)
        }) ?: return SaveOutcome.Cancelled
        return withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw FrontendFailure("The selected destination could not be opened.")
            output.use { it.write(bytes); it.flush() }
            SaveOutcome.Saved(name)
        }
    }

    private suspend fun choose(intent: Intent): Uri? = withContext(Dispatchers.Main.immediate) {
        val activity = context.findActivity() ?: throw FrontendFailure("A visible activity is required to choose a file.")
        suspendCancellableCoroutine { continuation ->
            val fragment = WorkspacePickerFragment().apply {
                arguments = Bundle().apply { putParcelable("intent", intent) }
                result = continuation
            }
            activity.fragmentManager.beginTransaction().add(fragment, null).commitAllowingStateLoss()
            continuation.invokeOnCancellation {
                activity.runOnUiThread { fragment.dismiss() }
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** A native picker bridge avoids requiring changes to the activity or extra storage permissions. */
class WorkspacePickerFragment : Fragment() {
    internal var result: CancellableContinuation<Uri?>? = null
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onResume() {
        super.onResume()
        if (result?.isActive != true) { dismiss(); return }
        if (!launched) {
            launched = true
            try {
                val intent = arguments?.getParcelable<Intent>("intent") ?: error("Missing picker intent")
                startActivityForResult(intent, 42)
            } catch (error: Exception) {
                result?.takeIf { it.isActive }?.resumeWithException(error)
                result = null
                dismiss()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42) return
        result?.takeIf { it.isActive }?.resume(if (resultCode == Activity.RESULT_OK) data?.data else null)
        result = null
        dismiss()
    }

    override fun onDestroy() {
        result?.cancel()
        result = null
        super.onDestroy()
    }

    internal fun dismiss() {
        fragmentManager?.beginTransaction()?.remove(this)?.commitAllowingStateLoss()
    }
}
