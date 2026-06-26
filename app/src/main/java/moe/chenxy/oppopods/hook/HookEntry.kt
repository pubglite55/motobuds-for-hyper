package moe.chenxy.oppopods.hook

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.hook.milink.MiLinkServiceHook

class HookEntry : XposedModule() {
    private val TAG = "MotoBuds-HookEntry"
    private val configListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        when (param.packageName) {
            "com.android.bluetooth" -> {
                loadHook(HeadsetStateDispatcher, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), param.defaultClassLoader, param.packageName)
                // Register receiver and check for existing connections
                // Note: Context will be available when Bluetooth service starts
                Log.d(TAG, "Module loaded for com.android.bluetooth, waiting for Bluetooth service...")
            }
            //"com.android.settings" -> loadHook(SettingsHeadsetHook, param.defaultClassLoader, param.packageName)
            "com.milink.service" -> loadHook(MiLinkServiceHook, param.defaultClassLoader, param.packageName)
            "com.xiaomi.bluetooth" -> {
                loadHook(MiBluetoothToastHook, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), param.defaultClassLoader, param.packageName)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkExistingConnections() {
        try {
            // Use reflection to get BluetoothAdapter
            val btManagerClass = Class.forName("android.bluetooth.BluetoothManager")
            val btAdapterClass = Class.forName("android.bluetooth.BluetoothAdapter")
            val getAdapterMethod = btManagerClass.getMethod("getAdapter")

            // Get BluetoothManager from ServiceManager
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "bluetooth")

            // For now, log that we need to check connections
            Log.d(TAG, "Module loaded, checking for existing connections...")
            Log.d(TAG, "Note: Connection will be triggered when A2DP state changes or app sends connect request")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check existing connections", e)
        }
    }

    private fun loadHook(hook: HookContext, classLoader: ClassLoader, packageName: String) {
        Log.module = this
        hook.module = this
        hook.appClassLoader = classLoader
        hook.packageName = packageName
        hook.prefs = getRemotePreferences("motobuds_settings")
        Log.d(TAG, "loadHook package=$packageName hook=${hook.javaClass.simpleName}")
        ConfigManager.init(hook.prefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(sharedPreferences)
            }
        }
        configListeners.add(configListener)
        hook.prefs.registerOnSharedPreferenceChangeListener(configListener)
        hook.onHook()
    }
}
