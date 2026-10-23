package moe.chenxy.oppopods.hook

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.hook.milink.MiLinkServiceHook

class HookEntry : XposedModule() {
    private val TAG = "MotoBuds-HookEntry"
    private val activeHooks = mutableListOf<HookContext>()
    private val configListeners = mutableListOf<Pair<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>>()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        loadHooksForPackage(param.packageName, param.defaultClassLoader)
    }

    /**
     * API 102 hot reload: release everything the hooks cached inside the target process, then
     * detach the current hook handles. Returning true tells the framework the module accepts
     * a hot reload instead of waiting for a process restart.
     */
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        activeHooks.forEach { hook ->
            runCatching { hook.onHotReloading() }
                .onFailure { Log.w(TAG, "onHotReloading failed for ${hook.javaClass.simpleName}", it) }
        }
        activeHooks.clear()
        configListeners.forEach { (prefs, listener) ->
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        configListeners.clear()
        detach()
        return true
    }

    /**
     * API 102 hot reload: re-apply the hooks against the class loader the old handles were
     * using, then unhook whatever the reloaded module no longer registers.
     */
    override fun onHotReloaded(param: HotReloadedParam) {
        val oldHooks = param.oldHookHandles
        val classLoader = oldHooks.firstOrNull()?.executable?.declaringClass?.classLoader
        if (classLoader == null) {
            android.util.Log.w(TAG, "Hot reload skipped: no target class loader is available")
            return
        }
        // processName can carry a sub-process suffix (e.g. com.milink.service:ui), while hook
        // selection is keyed by the owning package name.
        val packageName = param.processName.substringBefore(':')
        android.util.Log.d(TAG, "Hot reload package=$packageName process=${param.processName}")
        loadHooksForPackage(packageName, classLoader)
        val activeIds = activeHooks.flatMap { it.hookIds() }
        oldHooks.filter { it.id !in activeIds }.forEach(HookHandle::unhook)
    }

    private fun loadHooksForPackage(packageName: String, classLoader: ClassLoader) {
        when (packageName) {
            "com.android.bluetooth" -> {
                loadHook(HeadsetStateDispatcher, classLoader, packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), classLoader, packageName)
            }
            //"com.android.settings" -> loadHook(SettingsHeadsetHook, classLoader, packageName)
            "com.milink.service" -> loadHook(MiLinkServiceHook, classLoader, packageName)
            "com.xiaomi.bluetooth" -> {
                loadHook(MiBluetoothToastHook, classLoader, packageName)
                loadHook(BluetoothUpstreamHeadsetHook(), classLoader, packageName)
                // HyperOS 4 / A17 hides the wireless_headset icon from this process.
                loadHook(XiaomiBluetoothHeadsetIconGuard, classLoader, packageName)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadHook(hook: HookContext, classLoader: ClassLoader, packageName: String) {
        Log.module = this
        hook.module = this
        hook.appClassLoader = classLoader
        hook.packageName = packageName
        hook.prefs = getRemotePreferences("motobuds_settings")
        activeHooks.add(hook)
        Log.d(TAG, "loadHook package=$packageName hook=${hook.javaClass.simpleName}")
        ConfigManager.init(hook.prefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(sharedPreferences)
            }
        }
        configListeners.add(hook.prefs to configListener)
        hook.prefs.registerOnSharedPreferenceChangeListener(configListener)
        hook.onHook()
    }
}
