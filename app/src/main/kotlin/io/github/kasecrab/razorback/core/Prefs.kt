package io.github.kasecrab.razorback.core

import android.content.Context
import android.content.SharedPreferences

/** A named preference with a default; the default's type is the stored type. */
class Key<T : Any>(val name: String, val default: T)

class EnumKey<E : Enum<E>>(val name: String, val default: E, val values: Array<E>)

/**
 * Typed wrapper over one SharedPreferences file with a change fan-out. The object itself
 * is the platform listener: SharedPreferences only holds listeners weakly, and a separate
 * listener field is exactly what R8 folds away in release builds, after which the first
 * garbage collection silently ends every theme and model update.
 */
class Prefs(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val sp: SharedPreferences = context.getSharedPreferences("razorback", Context.MODE_PRIVATE)
    private val listeners = ArrayList<(String) -> Unit>(4)

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == null) return
        // Copy: a listener may add or remove listeners while being told.
        for (l in listeners.toTypedArray()) l(key)
    }

    fun onChange(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeOnChange(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: Key<T>): T = when (val d = key.default) {
        is String -> (sp.getString(key.name, d) ?: d) as T
        is Boolean -> sp.getBoolean(key.name, d) as T
        is Int -> sp.getInt(key.name, d) as T
        is Long -> sp.getLong(key.name, d) as T
        is Float -> sp.getFloat(key.name, d) as T
        else -> throw IllegalArgumentException("unsupported pref type for ${key.name}")
    }

    operator fun <T : Any> set(key: Key<T>, value: T) {
        val e = sp.edit()
        when (value) {
            is String -> e.putString(key.name, value)
            is Boolean -> e.putBoolean(key.name, value)
            is Int -> e.putInt(key.name, value)
            is Long -> e.putLong(key.name, value)
            is Float -> e.putFloat(key.name, value)
            else -> throw IllegalArgumentException("unsupported pref type for ${key.name}")
        }
        e.apply()
    }

    operator fun <E : Enum<E>> get(key: EnumKey<E>): E {
        val name = sp.getString(key.name, null) ?: return key.default
        return key.values.firstOrNull { it.name == name } ?: key.default
    }

    operator fun <E : Enum<E>> set(key: EnumKey<E>, value: E) {
        sp.edit().putString(key.name, value.name).apply()
    }

    fun rawString(name: String): String? = sp.getString(name, null)

    fun putRawString(name: String, value: String?) {
        sp.edit().apply { if (value == null) remove(name) else putString(name, value) }.apply()
    }

    fun putRawBoolean(name: String, value: Boolean) = sp.edit().putBoolean(name, value).apply()

    fun putRawInt(name: String, value: Int) = sp.edit().putInt(name, value).apply()

    fun putRawLong(name: String, value: Long) = sp.edit().putLong(name, value).apply()

    fun putRawFloat(name: String, value: Float) = sp.edit().putFloat(name, value).apply()

    fun removeAll(prefix: String) {
        val e = sp.edit()
        for (k in sp.all.keys) if (k.startsWith(prefix)) e.remove(k)
        e.apply()
    }

    fun contains(name: String): Boolean = sp.contains(name)

    /** Whether anything at all is stored under [prefix]. */
    fun anyUnder(prefix: String): Boolean = sp.all.keys.any { it.startsWith(prefix) }

    /** Everything except secrets, for settings export. */
    fun snapshot(): Map<String, Any?> = sp.all.filterKeys { !it.startsWith(Secrets.PREFIX) }
}
