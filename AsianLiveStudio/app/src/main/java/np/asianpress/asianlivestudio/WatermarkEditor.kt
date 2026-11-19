package np.asianpress.asianlivestudio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Final watermark/template editor. All state is local to the device. */
class WatermarkEditor(
    private val context: Context,
    private val canvas: FrameLayout,
    private val prefs: android.content.SharedPreferences
) {
    private var selected: View? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var editing = false

    fun begin() {
        editing = true
        canvas.isClickable = true
        for (i in 0 until canvas.childCount) attach(canvas.getChildAt(i))
        Toast.makeText(context, "Tap a layer and drag it. Use Watermark Editor for controls.", Toast.LENGTH_LONG).show()
    }

    fun end() {
        editing = false
        selected = null
        canvas.isClickable = false
    }

    fun addText(text: String) {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(14, 8, 14, 8)
            background = rounded(0x99000000.toInt(), 10f)
            tag = "custom_text"
        }
        addAt(tv, 28, (canvas.height * .35f).toInt())
    }

    fun addBadge() {
        val tv = TextView(context).apply {
            text = "● LIVE"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(12, 7, 12, 7)
            background = rounded(0xFFC62828.toInt(), 14f)
            tag = "custom_badge"
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.END
        lp.setMargins(0, 24, 24, 0)
        canvas.addView(tv, lp)
        attach(tv)
        select(tv)
    }

    fun addImage(uri: Uri) {
        val iv = ImageView(context).apply {
            setImageURI(uri)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            tag = "custom_image"
            setTag(IMAGE_URI_TAG, uri.toString())
        }
        val lp = FrameLayout.LayoutParams(dp(140), dp(90))
        lp.leftMargin = 28
        lp.topMargin = (canvas.height * .5f).toInt().coerceAtLeast(30)
        canvas.addView(iv, lp)
        attach(iv)
        select(iv)
    }

    fun addTimestamp() {
        val tv = TextView(context).apply {
            text = "--/--/---- --:--:--"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setPadding(10, 6, 10, 6)
            background = rounded(0x88000000.toInt(), 8f)
            tag = "custom_datetime"
        }
        addAt(tv, 24, (canvas.height * .72f).toInt())
    }

    fun duplicateSelected() {
        val source = selected ?: return
        val copy: View = when (source) {
            is TextView -> TextView(context).apply {
                text = source.text
                textSize = source.textSize / context.resources.displayMetrics.scaledDensity
                setTextColor(source.currentTextColor)
                typeface = source.typeface
                setPadding(source.paddingLeft, source.paddingTop, source.paddingRight, source.paddingBottom)
                background = source.background?.constantState?.newDrawable()?.mutate()
                alpha = source.alpha
                tag = source.tag?.toString() ?: "custom_text"
            }
            is ImageView -> ImageView(context).apply {
                setImageDrawable(source.drawable)
                scaleType = source.scaleType
                setTag(IMAGE_URI_TAG, source.getTag(IMAGE_URI_TAG))
                alpha = source.alpha
                tag = "custom_image"
            }
            else -> return
        }
        val lp = FrameLayout.LayoutParams(source.width.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            source.height.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = (source.x + 24).toInt().coerceAtLeast(0)
        lp.topMargin = (source.y + 24).toInt().coerceAtLeast(0)
        canvas.addView(copy, lp)
        copy.scaleX = source.scaleX
        copy.scaleY = source.scaleY
        attach(copy)
        select(copy)
    }

    fun deleteSelected() {
        selected?.let { if (it.parent === canvas) canvas.removeView(it) }
        selected = null
    }

    fun scaleSelected(factor: Float) {
        selected?.let { v ->
            v.scaleX = min(4f, max(.35f, v.scaleX * factor))
            v.scaleY = min(4f, max(.35f, v.scaleY * factor))
        }
    }

    fun setSelectedPosition(xPercent: Int, yPercent: Int) {
        val v = selected ?: return
        val maxX = max(0f, canvas.width - v.width.toFloat())
        val maxY = max(0f, canvas.height - v.height.toFloat())
        v.x = maxX * xPercent.coerceIn(0, 100) / 100f
        v.y = maxY * yPercent.coerceIn(0, 100) / 100f
    }

    fun bringForward() {
        selected?.let { v ->
            val idx = canvas.indexOfChild(v)
            if (idx >= 0 && idx < canvas.childCount - 1) {
                canvas.removeViewAt(idx); canvas.addView(v, idx + 1)
            }
        }
    }

    fun sendBackward() {
        selected?.let { v ->
            val idx = canvas.indexOfChild(v)
            if (idx > 0) {
                canvas.removeViewAt(idx); canvas.addView(v, idx - 1)
            }
        }
    }

    fun setSelectedTextSize(sp: Float) {
        val v = selected
        if (v is TextView) v.textSize = sp.coerceIn(8f, 120f)
    }

    fun setSelectedTextColor(color: Int) {
        val v = selected
        if (v is TextView) v.setTextColor(color)
    }

    fun setSelectedFont(font: String) {
        val v = selected
        if (v is TextView) {
            v.typeface = when (font) {
                "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                "SANS" -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                else -> Typeface.DEFAULT_BOLD
            }
        }
    }

    fun setSelectedBackground(color: Int) {
        val v = selected
        if (v is TextView) v.background = rounded(color, 10f)
    }

    fun setSelectedOpacity(percent: Int) {
        selected?.alpha = percent.coerceIn(10, 100) / 100f
    }

    fun selectedDescription(): String = when (val v = selected) {
        null -> "Nothing selected"
        is TextView -> "Text: ${v.text}"
        is ImageView -> "Image layer"
        else -> "Layer"
    }

    fun templateNames(): List<String> = prefs.getStringSet("template_names", emptySet())?.toList()?.sorted() ?: emptyList()

    fun saveTemplate(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val root = JSONObject().put("version", 2).put("name", clean).put("width", canvas.width).put("height", canvas.height)
        val layers = JSONArray()
        for (i in 0 until canvas.childCount) layers.put(viewToJson(canvas.getChildAt(i), i))
        root.put("layers", layers)
        prefs.edit().putString(templateKey(clean), root.toString())
            .putStringSet("template_names", (templateNames() + clean).toSet()).apply()
    }

    fun loadTemplate(name: String): Boolean {
        val raw = prefs.getString(templateKey(name), null) ?: return false
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val layers = root.optJSONArray("layers") ?: return false

        // Built-in profile watermark is retained. Custom layers are replaced.
        for (i in canvas.childCount - 1 downTo 0) {
            val v = canvas.getChildAt(i)
            if (v.tag?.toString()?.startsWith("custom_") == true) canvas.removeViewAt(i)
        }
        for (i in 0 until min(layers.length(), canvas.childCount)) {
            applyGeometry(canvas.getChildAt(i), layers.optJSONObject(i) ?: continue)
        }
        for (i in 0 until layers.length()) {
            val o = layers.getJSONObject(i)
            val tag = o.optString("tag")
            if (!tag.startsWith("custom_")) continue
            when (o.optString("type")) {
                "text" -> {
                    val tv = TextView(context).apply {
                        text = o.optString("text")
                        textSize = o.optDouble("textSize", 18.0).toFloat()
                        setTextColor(o.optInt("textColor", Color.WHITE))
                        typeface = when (o.optString("font", "SANS")) {
                            "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                            "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                            else -> Typeface.DEFAULT_BOLD
                        }
                        setPadding(o.optInt("pl", 12), o.optInt("pt", 8), o.optInt("pr", 12), o.optInt("pb", 8))
                        background = rounded(o.optInt("background", 0x99000000.toInt()), 10f)
                        this.tag = tag
                    }
                    canvas.addView(tv); applyGeometry(tv, o); attach(tv)
                }
                "image" -> {
                    val uri = o.optString("uri")
                    if (uri.isNotBlank()) {
                        val iv = ImageView(context).apply {
                            setImageURI(Uri.parse(uri)); scaleType = ImageView.ScaleType.FIT_CENTER; this.tag = tag; setTag(IMAGE_URI_TAG, uri)
                        }
                        canvas.addView(iv); applyGeometry(iv, o); attach(iv)
                    }
                }
            }
        }
        return true
    }

    fun deleteTemplate(name: String) {
        prefs.edit().remove(templateKey(name))
            .putStringSet("template_names", templateNames().filter { it != name }.toSet()).apply()
    }

    fun exportTemplate(name: String): String? = prefs.getString(templateKey(name), null)

    fun importTemplate(json: String, requestedName: String? = null): String? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val name = (requestedName ?: root.optString("name").ifBlank { "Imported Template" }).trim()
        if (name.isBlank()) return null
        root.put("name", name)
        prefs.edit().putString(templateKey(name), root.toString())
            .putStringSet("template_names", (templateNames() + name).toSet()).apply()
        return name
    }

    private fun viewToJson(v: View, z: Int): JSONObject {
        val o = JSONObject().put("type", when (v) { is TextView -> "text"; is ImageView -> "image"; else -> "view" })
            .put("tag", v.tag?.toString() ?: "")
            .put("x", v.x).put("y", v.y).put("width", v.width).put("height", v.height)
            .put("scaleX", v.scaleX).put("scaleY", v.scaleY).put("alpha", v.alpha)
            .put("visible", v.visibility == View.VISIBLE).put("z", z)
        if (v is TextView) {
            o.put("text", v.text?.toString() ?: "")
            o.put("textSize", v.textSize / context.resources.displayMetrics.scaledDensity)
            o.put("textColor", v.currentTextColor)
            o.put("font", if (v.typeface == Typeface.MONOSPACE) "MONOSPACE" else if (v.typeface == Typeface.SERIF) "SERIF" else "SANS")
            o.put("pl", v.paddingLeft).put("pt", v.paddingTop).put("pr", v.paddingRight).put("pb", v.paddingBottom)
            val bg = v.background as? GradientDrawable
            o.put("background", if (bg != null) 0x99000000.toInt() else 0x00000000)
        }
        if (v is ImageView) {
            val tag = v.tag?.toString() ?: ""
            if (tag == "custom_image") o.put("uri", findImageUri(v))
        }
        return o
    }

    private fun findImageUri(v: ImageView): String {
        return (v.getTag(IMAGE_URI_TAG) as? String) ?: ""
    }

    private fun applyGeometry(v: View, o: JSONObject) {
        v.x = o.optDouble("x", v.x.toDouble()).toFloat()
        v.y = o.optDouble("y", v.y.toDouble()).toFloat()
        v.scaleX = o.optDouble("scaleX", 1.0).toFloat()
        v.scaleY = o.optDouble("scaleY", 1.0).toFloat()
        v.alpha = o.optDouble("alpha", 1.0).toFloat()
        v.visibility = if (o.optBoolean("visible", true)) View.VISIBLE else View.GONE
        val w = o.optInt("width", 0); val h = o.optInt("height", 0)
        if (w > 0 && h > 0) v.layoutParams = FrameLayout.LayoutParams(w, h)
    }

    private fun addAt(v: View, x: Int, y: Int) {
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = x; lp.topMargin = y.coerceAtLeast(20)
        canvas.addView(v, lp); attach(v); select(v)
    }

    private fun attach(v: View) {
        if (v.getTag(R.id.editor_attached_tag) == true) return
        v.setTag(R.id.editor_attached_tag, true)
        v.setOnTouchListener { view, event ->
            if (!editing) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    select(view); downX = event.rawX; downY = event.rawY; startX = view.x; startY = view.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    val maxX = max(0f, canvas.width - view.width.toFloat()); val maxY = max(0f, canvas.height - view.height.toFloat())
                    view.x = (startX + dx).coerceIn(0f, maxX); view.y = (startY + dy).coerceIn(0f, maxY); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun select(v: View) { selected = v; Toast.makeText(context, selectedDescription(), Toast.LENGTH_SHORT).show() }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun templateKey(name: String) = "watermark_template_${name.trim()}"

    companion object { private const val IMAGE_URI_TAG = 0x7f0a0101 }
}
