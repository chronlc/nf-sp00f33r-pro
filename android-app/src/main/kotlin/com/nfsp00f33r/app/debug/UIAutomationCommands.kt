package com.nfsp00f33r.app.debug

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * UI Automation Commands for ADB Debug System
 * Provides UI inspection, interaction, and testing capabilities
 */
class UIAutomationCommands(private val context: Context) {
    
    companion object {
        private const val TAG = "UIAutomation"
    }
    
    /**
     * Get current activity and screen information
     */
    suspend fun dumpUI(): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("error", "No active activity found")
                return@withContext result
            }
            
            result.put("activity", activity.javaClass.simpleName)
            result.put("package", context.packageName)
            
            // Get current screen name from activity
            val screenName = getScreenName(activity)
            result.put("screen", screenName)
            
            // Get visible views
            val views = JSONArray()
            val rootView = activity.window.decorView.rootView
            traverseViews(rootView, views)
            
            result.put("views", views)
            result.put("view_count", views.length())
            
            Timber.d("$TAG UI dump: ${views.length()} views found")
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to dump UI")
            JSONObject().apply {
                put("error", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Find UI element by various criteria
     */
    suspend fun findElement(params: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val text = params.optString("text", null)
            val id = params.optString("id", null)
            val type = params.optString("type", null)
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("found", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            val rootView = activity.window.decorView.rootView
            val foundView = findViewRecursive(rootView, text, id, type)
            
            if (foundView != null) {
                result.put("found", true)
                result.put("element", viewToJson(foundView))
            } else {
                result.put("found", false)
                result.put("searched_for", JSONObject().apply {
                    if (text != null) put("text", text)
                    if (id != null) put("id", id)
                    if (type != null) put("type", type)
                })
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to find element")
            JSONObject().apply {
                put("found", false)
                put("error", e.message)
            }
        }
    }
    
    /**
     * Click UI element
     */
    suspend fun clickElement(params: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val text = params.optString("text", null)
            val id = params.optString("id", null)
            val x = params.optInt("x", -1)
            val y = params.optInt("y", -1)
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("success", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            // Click by coordinates
            if (x >= 0 && y >= 0) {
                val clicked = clickAtCoordinates(activity, x, y)
                result.put("success", clicked)
                result.put("method", "coordinates")
                result.put("x", x)
                result.put("y", y)
                return@withContext result
            }
            
            // Find and click view
            val rootView = activity.window.decorView.rootView
            val view = findViewRecursive(rootView, text, id, null)
            
            if (view != null) {
                view.performClick()
                result.put("success", true)
                result.put("method", "view_click")
                result.put("clicked", viewToJson(view))
                
                Timber.i("$TAG Clicked view: ${view.javaClass.simpleName}")
            } else {
                result.put("success", false)
                result.put("error", "Element not found")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to click element")
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
        }
    }
    
    /**
     * Input text into element
     */
    suspend fun inputText(params: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val text = params.getString("text")
            val target = params.optString("target", null)
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("success", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            // Find input field
            val rootView = activity.window.decorView.rootView
            val view = findViewRecursive(rootView, target, target, "EditText")
            
            if (view is android.widget.EditText) {
                view.setText(text)
                result.put("success", true)
                result.put("text", text)
                
                Timber.i("$TAG Input text: $text")
            } else {
                result.put("success", false)
                result.put("error", "EditText not found")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to input text")
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
        }
    }
    
    /**
     * Capture screenshot
     */
    suspend fun captureScreenshot(params: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val path = params.optString("path", "/sdcard/debug_screenshot.png")
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("success", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            val rootView = activity.window.decorView.rootView
            rootView.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(rootView.drawingCache)
            rootView.isDrawingCacheEnabled = false
            
            val file = File(path)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            result.put("success", true)
            result.put("path", path)
            result.put("width", bitmap.width)
            result.put("height", bitmap.height)
            
            Timber.i("$TAG Screenshot saved: $path")
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to capture screenshot")
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
        }
    }
    
    /**
     * Get current screen hierarchy
     */
    suspend fun dumpHierarchy(): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("error", "No active activity")
                return@withContext result
            }
            
            val rootView = activity.window.decorView.rootView
            val hierarchy = buildHierarchy(rootView, 0)
            
            result.put("hierarchy", hierarchy)
            result.put("activity", activity.javaClass.simpleName)
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to dump hierarchy")
            JSONObject().apply {
                put("error", e.message)
            }
        }
    }
    
    /**
     * Assert element is visible
     */
    suspend fun assertVisible(params: JSONObject): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val target = params.getString("target")
            val expected = params.optBoolean("expected", true)
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("passed", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            val rootView = activity.window.decorView.rootView
            val view = findViewRecursive(rootView, target, target, null)
            
            val isVisible = view != null && view.visibility == View.VISIBLE
            val passed = isVisible == expected
            
            result.put("passed", passed)
            result.put("target", target)
            result.put("expected", expected)
            result.put("actual", isVisible)
            
            if (!passed) {
                result.put("error", "Assertion failed: expected visible=$expected, got visible=$isVisible")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed assertion")
            JSONObject().apply {
                put("passed", false)
                put("error", e.message)
            }
        }
    }
    
    /**
     * Navigate back
     */
    suspend fun navigateBack(): JSONObject = withContext(Dispatchers.Main) {
        try {
            val result = JSONObject()
            
            val activity = getCurrentActivity()
            if (activity == null) {
                result.put("success", false)
                result.put("error", "No active activity")
                return@withContext result
            }
            
            activity.onBackPressed()
            result.put("success", true)
            
            Timber.i("$TAG Back navigation triggered")
            
            result
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to navigate back")
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
        }
    }
    
    // Helper methods
    
    private fun getCurrentActivity(): Activity? {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            
            val activities = activitiesField.get(activityThread) as? Map<*, *>
            activities?.values?.forEach { activityRecord ->
                val activityRecordClass = activityRecord?.javaClass
                val pausedField = activityRecordClass?.getDeclaredField("paused")
                pausedField?.isAccessible = true
                
                if (pausedField?.getBoolean(activityRecord) == false) {
                    val activityField = activityRecordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    return activityField.get(activityRecord) as? Activity
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to get current activity")
        }
        return null
    }
    
    private fun getScreenName(activity: Activity): String {
        // Try to get screen name from fragment or activity
        val fragmentManager = activity.fragmentManager
        val fragment = fragmentManager?.findFragmentById(android.R.id.content)
        return fragment?.javaClass?.simpleName ?: activity.javaClass.simpleName
    }
    
    private fun traverseViews(view: View, result: JSONArray) {
        if (view.visibility == View.VISIBLE) {
            result.put(viewToJson(view))
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseViews(view.getChildAt(i), result)
            }
        }
    }
    
    private fun viewToJson(view: View): JSONObject {
        return JSONObject().apply {
            put("type", view.javaClass.simpleName)
            put("id", view.id)
            put("visible", view.visibility == View.VISIBLE)
            put("enabled", view.isEnabled)
            put("clickable", view.isClickable)
            
            // Get bounds
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            put("bounds", JSONObject().apply {
                put("left", rect.left)
                put("top", rect.top)
                put("right", rect.right)
                put("bottom", rect.bottom)
            })
            
            // Get text if available
            when (view) {
                is android.widget.TextView -> put("text", view.text.toString())
                is android.widget.Button -> put("text", view.text.toString())
                is android.widget.EditText -> put("text", view.text.toString())
            }
        }
    }
    
    private fun findViewRecursive(view: View, text: String?, id: String?, type: String?): View? {
        // Check current view
        if (matchesView(view, text, id, type)) {
            return view
        }
        
        // Recursively check children
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findViewRecursive(view.getChildAt(i), text, id, type)
                if (found != null) return found
            }
        }
        
        return null
    }
    
    private fun matchesView(view: View, text: String?, id: String?, type: String?): Boolean {
        if (type != null && !view.javaClass.simpleName.contains(type, ignoreCase = true)) {
            return false
        }
        
        if (text != null) {
            val viewText = when (view) {
                is android.widget.TextView -> view.text.toString()
                is android.widget.Button -> view.text.toString()
                else -> null
            }
            if (viewText == null || !viewText.contains(text, ignoreCase = true)) {
                return false
            }
        }
        
        return true
    }
    
    private fun clickAtCoordinates(activity: Activity, x: Int, y: Int): Boolean {
        val rootView = activity.window.decorView.rootView
        val view = findViewAtCoordinates(rootView, x, y)
        
        return if (view != null) {
            view.performClick()
            true
        } else {
            false
        }
    }
    
    private fun findViewAtCoordinates(view: View, x: Int, y: Int): View? {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        
        if (rect.contains(x, y) && view.visibility == View.VISIBLE && view.isClickable) {
            return view
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findViewAtCoordinates(view.getChildAt(i), x, y)
                if (found != null) return found
            }
        }
        
        return null
    }
    
    private fun buildHierarchy(view: View, depth: Int): JSONObject {
        val node = JSONObject().apply {
            put("type", view.javaClass.simpleName)
            put("id", view.id)
            put("depth", depth)
            put("visible", view.visibility == View.VISIBLE)
            
            when (view) {
                is android.widget.TextView -> put("text", view.text.toString())
                is android.widget.Button -> put("text", view.text.toString())
            }
            
            if (view is ViewGroup) {
                val children = JSONArray()
                for (i in 0 until view.childCount) {
                    children.put(buildHierarchy(view.getChildAt(i), depth + 1))
                }
                put("children", children)
                put("child_count", view.childCount)
            }
        }
        
        return node
    }
}
