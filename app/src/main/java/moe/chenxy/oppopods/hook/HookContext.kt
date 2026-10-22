package moe.chenxy.oppopods.hook

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import moe.chenxy.oppopods.config.ConfigManager

abstract class HookContext {
    lateinit var module: XposedModule
    lateinit var appClassLoader: ClassLoader
    lateinit var prefs: SharedPreferences
    lateinit var packageName: String

    private val hookHandles = mutableListOf<XposedInterface.HookHandle>()

    abstract fun onHook()

    /**
     * Releases resources retained by target-process objects (receivers, handlers, caches)
     * before the module is hot reloaded. Required by libxposed API 102.
     */
    open fun onHotReloading() = Unit

    fun hookIds(): Set<String> {
        return if (module.getApiVersion() >= XposedInterface.API_102) {
            hookHandles.mapNotNullTo(linkedSetOf()) { it.id }
        } else {
            emptySet()
        }
    }

    fun fakeDeviceId(): String = ConfigManager.fakeDeviceId()

    fun fakeSupport(): String = ConfigManager.fakeSupport()

    fun refreshConfig() {
        ConfigManager.refreshFromPrefs(prefs)
    }

    fun reloadRemotePrefs() {
        runCatching {
            prefs.javaClass.methods.firstOrNull {
                it.name == "reload" && it.parameterTypes.isEmpty()
            }?.invoke(prefs)
        }
    }

    fun findClass(name: String): Class<*> = Class.forName(name, false, appClassLoader)

    fun findMethod(className: String, methodName: String, vararg parameterTypes: Class<*>): Method =
        findClass(className).getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }

    fun findConstructor(className: String, vararg parameterTypes: Class<*>): Constructor<*> =
        findClass(className).getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }

    fun findMethodByParamCount(className: String, methodName: String, paramCount: Int): Method =
        findClass(className).declaredMethods.first { it.name == methodName && it.parameterTypes.size == paramCount }
            .apply { isAccessible = true }

    fun findConstructorByParamCount(className: String, paramCount: Int): Constructor<*> =
        findClass(className).declaredConstructors.first { it.parameterTypes.size == paramCount }
            .apply { isAccessible = true }

    fun hookAfter(method: Method, block: HookParam.() -> Unit) {
        registerHook(method, "after") { chain ->
            val result = chain.proceed()
            HookParam(chain, result).apply(block).result
        }
    }

    fun hookBefore(method: Method, block: HookParam.() -> Unit) {
        registerHook(method, "before") { chain ->
            val param = HookParam(chain, null).apply(block)
            if (param.hasResult) param.result else chain.proceed()
        }
    }

    fun hookConstructorAfter(constructor: Constructor<*>, block: HookParam.() -> Unit) {
        registerHook(constructor, "constructor-after") { chain ->
            chain.proceed().also { HookParam(chain, it).apply(block) }
        }
    }

    /**
     * Registers a hook with a stable id so API 102 hot reload can tell which hooks the reloaded
     * module still owns. On API 101 frameworks the id is not set and the hook is registered
     * exactly like before.
     */
    private fun registerHook(executable: Executable, phase: String, hooker: XposedInterface.Hooker) {
        val id = "$phase:${executable.toGenericString()}"
        val builder = module.hook(executable)
        if (module.getApiVersion() >= XposedInterface.API_102) {
            builder.setId(id)
        }
        hookHandles += builder.intercept(hooker)
    }
}

object Log {
    @Volatile
    var module: XposedModule? = null

    fun v(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun i(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun d(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun d(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_DEBUG) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.INFO, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        if (ConfigManager.logLevel() < ConfigManager.LOG_LEVEL_BASIC) return
        module?.log(android.util.Log.ERROR, tag, message, throwable)
    }
}

class HookParam(private val chain: XposedInterface.Chain, initialResult: Any?) {
    val args: List<Any?> = chain.args
    val instance: Any? = chain.thisObject
    var hasResult = false
        private set
    var result: Any? = initialResult
        set(value) {
            hasResult = true
            field = value
        }
}

fun getObjectField(instance: Any?, fieldName: String): Any? {
    if (instance == null) return null
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        runCatching {
            return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
        }
        cls = cls.superclass
    }
    throw NoSuchFieldException(fieldName)
}

fun setObjectField(instance: Any?, fieldName: String, value: Any?) {
    if (instance == null) return
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        runCatching {
            cls.getDeclaredField(fieldName).apply { isAccessible = true }.set(instance, value)
            return
        }
        cls = cls.superclass
    }
    throw NoSuchFieldException(fieldName)
}

fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
    if (instance == null) return null
    var cls: Class<*>? = instance.javaClass
    while (cls != null) {
        cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
            it.isAccessible = true
            return it.invoke(instance, *args)
        }
        cls = cls.superclass
    }
    throw NoSuchMethodException(methodName)
}
