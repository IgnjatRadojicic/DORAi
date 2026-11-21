package dorai.app.shell

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dorai.app.auth.KeystoreTokenVault
import dorai.app.connect.DataStoreSettings
import okio.Path.Companion.toPath
import java.io.File

private var storage: AppStorage? = null

/**
 * One storage for the whole process (DataStore allows a single instance per file), so a recreated
 * activity gets the same one. Settings and the encrypted tokens share one preferences file.
 */
fun androidStorage(context: Context): AppStorage = synchronized(AppStorage::class) {
    storage ?: run {
        val file = File(context.applicationContext.filesDir, "dorai.preferences_pb")
        val store = PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
        AppStorage(DataStoreSettings(store), KeystoreTokenVault(store))
    }.also { storage = it }
}
