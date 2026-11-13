package org.librepaw.app.testing

import android.content.SharedPreferences

/** A plain in-memory [SharedPreferences], so the stores built on it can be unit tested on the JVM. */
class InMemorySharedPreferences : SharedPreferences {
  private val values = mutableMapOf<String, Any?>()

  override fun getAll(): Map<String, *> = synchronized(values) { values.toMap() }

  override fun getString(key: String, defValue: String?): String? = synchronized(values) { values[key] as? String ?: defValue }

  @Suppress("UNCHECKED_CAST")
  override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
    synchronized(values) { values[key] as? Set<String> ?: defValues }

  override fun getInt(key: String, defValue: Int): Int = synchronized(values) { values[key] as? Int ?: defValue }

  override fun getLong(key: String, defValue: Long): Long = synchronized(values) { values[key] as? Long ?: defValue }

  override fun getFloat(key: String, defValue: Float): Float = synchronized(values) { values[key] as? Float ?: defValue }

  override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(values) { values[key] as? Boolean ?: defValue }

  override fun contains(key: String): Boolean = synchronized(values) { key in values }

  override fun edit(): SharedPreferences.Editor = Editor()

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

  private inner class Editor : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()
    private var clearAll = false

    override fun putString(key: String, value: String?) = apply { pending[key] = value }

    override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }

    override fun putInt(key: String, value: Int) = apply { pending[key] = value }

    override fun putLong(key: String, value: Long) = apply { pending[key] = value }

    override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

    override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

    override fun remove(key: String) = apply { removed += key }

    override fun clear() = apply { clearAll = true }

    override fun commit(): Boolean {
      synchronized(values) {
        if (clearAll) values.clear()
        removed.forEach(values::remove)
        values.putAll(pending)
      }
      return true
    }

    override fun apply() {
      commit()
    }
  }
}
