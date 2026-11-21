package dorai.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dorai.app.App
import dorai.app.OpenedInvite
import dorai.app.shell.androidStorage

class MainActivity : ComponentActivity() {
    /** The invite link from the admin's email, when the app was opened by tapping it. */
    private var invite by mutableStateOf<OpenedInvite?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the activity is recreated with the same intent; the link was already used then.
        if (savedInstanceState == null) invite = intent.inviteLink()
        val storage = androidStorage(this)
        setContent { App(invite = invite, storage = storage) }
    }

    /** The app was already open (singleTop) and another invite link was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.inviteLink()?.let { invite = it }
    }

    private fun Intent.inviteLink(): OpenedInvite? =
        data?.takeIf { action == Intent.ACTION_VIEW && it.scheme == "dorai" && it.host == "connect" }
            ?.let { OpenedInvite(it.toString()) }
}
