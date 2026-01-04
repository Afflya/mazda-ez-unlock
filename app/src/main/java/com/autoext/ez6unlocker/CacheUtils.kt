package com.autoext.ez6unlocker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object CacheUtils {
    private const val PREFS_NAME = "dm_app_cache"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Stores a value in cache with the specified key
     * @param context Android context
     * @param key Cache key
     * @param value Value to store (supports String, Boolean, Int, Float, Long)
     */
    fun set(context: Context, key: String, value: Any?) {
        val sharedPreferences = getSharedPreferences(context)
        sharedPreferences.edit {

            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                null -> remove(key)
                else -> {
                    // For other types, convert to string representation
                    putString(key, value.toString())
                }
            }

        }
    }

    /**
     * Gets a boolean value from cache
     * @param context Android context
     * @param key Cache key
     * @param defaultValue Default value if key doesn't exist
     * @return Boolean value from cache or default value
     */
    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    /**
     * Gets a string value from cache
     * @param context Android context
     * @param key Cache key
     * @param defaultValue Default value if key doesn't exist
     * @return String value from cache or default value
     */
    fun getString(context: Context, key: String, defaultValue: String): String {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Gets an integer value from cache
     * @param context Android context
     * @param key Cache key
     * @param defaultValue Default value if key doesn't exist
     * @return Integer value from cache or default value
     */
    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.getInt(key, defaultValue)
    }

    /**
     * Gets a long value from cache
     * @param context Android context
     * @param key Cache key
     * @param defaultValue Default value if key doesn't exist
     * @return Long value from cache or default value
     */
    fun getLong(context: Context, key: String, defaultValue: Long): Long {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.getLong(key, defaultValue)
    }

    /**
     * Gets a float value from cache
     * @param context Android context
     * @param key Cache key
     * @param defaultValue Default value if key doesn't exist
     * @return Float value from cache or default value
     */
    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.getFloat(key, defaultValue)
    }

    /**
     * Checks if a key exists in cache
     * @param context Android context
     * @param key Cache key
     * @return True if key exists, false otherwise
     */
    fun contains(context: Context, key: String): Boolean {
        val sharedPreferences = getSharedPreferences(context)
        return sharedPreferences.contains(key)
    }

    /**
     * Removes a key-value pair from cache
     * @param context Android context
     * @param key Cache key to remove
     */
    fun remove(context: Context, key: String) {
        val sharedPreferences = getSharedPreferences(context)
        sharedPreferences.edit { remove(key) }
    }

    /**
     * Clears all cached data
     * @param context Android context
     */
    fun clear(context: Context) {
        val sharedPreferences = getSharedPreferences(context)
        sharedPreferences.edit { clear() }
    }
}