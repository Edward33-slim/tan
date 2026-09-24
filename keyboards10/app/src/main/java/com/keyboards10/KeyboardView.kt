package com.keyboards10

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class KeyboardView(context: Context, private val ime: KeyboardsImeService) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keys = ArrayList<Key>()
    private var downKey: Key? = null
    private var downX = 0f
    private var downY = 0f
    private var longPress = false
    private var diacriticsOpen = false
    private var clipboardOffset = 0f
    private var lastMoveY = 0f
    private var scrollingClipboard = false
    private var clipboardDownItem: ClipItem? = null
    private var clipboardDownIndex = -1
    private var clipboardSwipeHandled = false
    private var clipboardDownX = 0f
    private var clipboardDownY = 0f
    private var settingsOpen = false
    private var settingsDrag: Int = 0
    private var emojiOpen = false
    private var emojiCategory = 0
    private val longPressMs = 430L
    private val repeatDeleteMs = 115L
    private var backspaceRepeat = false
    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (!backspaceRepeat || downKey?.value != "BACKSPACE") return
            ime.deletePreviousWord()
            postDelayed(this, repeatDeleteMs)
        }
    }

    data class Key(val label: String, val value: String, val rect: RectF, val type: Int = 0)

    init {
        setBackgroundColor(0xFF191B22.toInt())
        isFocusable = false
    }

    // Give the non-fullscreen IME a real, finite height. Without this, some
    // Android/OEM IME hosts measure a custom View as the whole display.
    private fun desiredKeyboardHeightPx(): Int {
        val screenH = resources.displayMetrics.heightPixels
        // Map the keyboard-size slider to a clearly visible height range.
        // 0.35f = about 28% of the screen, 0.95f = about 55%.
        val f = ((ime.keyboardScale - 0.35f) / (0.95f - 0.35f)).coerceIn(0f, 1f)
        return (screenH * (0.28f + f * 0.27f)).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = desiredKeyboardHeightPx()
        val measuredW = MeasureSpec.getSize(widthMeasureSpec)
        val measuredH = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.AT_MOST -> min(desired, MeasureSpec.getSize(heightMeasureSpec))
            // The IME input view gets this exact size from its own layout params
            // after the slider is moved. Keep it so the keyboard can resize live.
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> desired
        }
        setMeasuredDimension(measuredW, measuredH)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        keys.clear()

        val w = width.toFloat()
        val contentTop = 0f
        val contentH = height.toFloat()
        val suggestionH = min(76f, contentH * 0.18f)

        if (ime.showClipboard) {
            drawSuggestionBar(c, suggestionH, contentTop)
            drawClipboard(c, contentTop + suggestionH)
            return
        }

        drawSuggestionBar(c, suggestionH, contentTop)

        if (emojiOpen) {
            drawEmojiPanel(c, contentTop + suggestionH)
            return
        }

        val rows = if (ime.arabic) arabicRows() else englishRows()
        val rowH = (contentH - suggestionH) / rows.size
        val gap = max(3f, w * 0.008f)

        rows.forEachIndexed { r, row ->
            val y = contentTop + suggestionH + r * rowH + gap
            val totalWeight = row.fold(0f) { total, item ->
                total + when (item.third) { 2 -> 2.65f; 3 -> 1.55f; else -> 1f }
            }
            val baseKeyW = (w - gap * (row.size + 1)) / totalWeight
            var x = gap
            row.forEach { triple ->
                val shownTriple = if (!ime.arabic && ime.shift && triple.second.length == 1 && triple.second[0].isLetter()) {
                    Triple(triple.first.uppercase(), triple.second.uppercase(), triple.third)
                } else triple
                val keyW = baseKeyW * when (shownTriple.third) { 2 -> 2.65f; 3 -> 1.55f; else -> 1f }
                val rect = RectF(
                    x,
                    y,
                    x + keyW,
                    y + rowH - gap
                )
                val key = Key(shownTriple.first, shownTriple.second, rect, shownTriple.third)
                keys += key
                drawKey(c, key)
                x += keyW + gap
            }
        }

        // Android-style key preview: draw the pressed key's bubble directly
        // above that key, never below the keyboard.
        if (downKey != null && !ime.showClipboard && !emojiOpen && !settingsOpen && isPreviewKey(downKey!!)) {
            drawKeyPreview(c, downKey!!)
        }

        if (diacriticsOpen) drawDiacriticsPopup(c)

        if (settingsOpen && !ime.showClipboard) {
            drawSettingsPanel(c, contentTop + suggestionH)
        }
    }

    private fun drawSuggestionBar(c: Canvas, h: Float, top: Float) {
        paint.color = 0xFF191B22.toInt()
        c.drawRect(0f, 0f, width.toFloat(), top, paint)
        paint.color = 0xFF2B2D35.toInt()
        c.drawRect(0f, top, width.toFloat(), top + h, paint)

        paint.color = 0xFFF0F0F0.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(27f, h * .44f)

        if (ime.showClipboard) {
            c.drawText("📋  الحافظة", width / 2f, top + h * .65f, paint)
        } else {
            val each = width / 5f
            c.drawText("📋", each * .5f, top + h * .65f, paint)
            c.drawText("⚙", each * 1.5f, top + h * .65f, paint)
            ime.currentSuggestions.take(3).forEachIndexed { i, word ->
                c.drawText(word, each * (i + 2.5f), top + h * .65f, paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT

        // The settings panel is drawn in onDraw() after the keyboard keys,
        // so it remains visible above them.
    }

    private fun drawSettingsPanel(c: Canvas, panelTop: Float) {
        val panelW = min(width * .86f, 560f)
        val panelH = min(height * .48f, 290f)
        val left = 10f
        val top = panelTop + 8f
        val right = left + panelW
        val bottom = min(height - 8f, top + panelH)

        paint.color = 0xFF1F2128.toInt()
        c.drawRoundRect(RectF(left, top, right, bottom), 14f, 14f, paint)

        paint.color = 0xFFF3F3F3.toInt()
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 18f
        c.drawText("إعدادات الكيبورد", right - 18f, top + 30f, paint)

        drawSlider(c, "حجم الكيبورد", ime.keyboardScale, 0.35f, 0.95f, top + 72f, left + 24f, right - 24f)
        drawSlider(c, "حجم الحروف والأرقام", ime.keyTextScale, 0.75f, 2.20f, top + 155f, left + 24f, right - 24f)

        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawSlider(c: Canvas, title: String, value: Float, minValue: Float, maxValue: Float, y: Float, left: Float, right: Float) {
        paint.color = 0xFFEAEAEA.toInt()
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 16f
        c.drawText(title, right, y - 18f, paint)

        val cy = y + 18f
        paint.color = 0xFF4A4C55.toInt()
        c.drawRoundRect(RectF(left, cy - 4f, right, cy + 4f), 5f, 5f, paint)

        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val knobX = left + (right - left) * fraction
        paint.color = 0xFFB7EA32.toInt()
        c.drawCircle(knobX, cy, 10f, paint)

        paint.color = 0xFFBFC0C5.toInt()
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 13f
        c.drawText("منخفض", left, cy + 30f, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("مرتفع", right, cy + 30f, paint)
    }

    private fun drawClipboard(c: Canvas, top: Float) {
        val rowH = 88f
        val availableH = (height - top).coerceAtLeast(1f)
        val maxOffset = max(0f, ime.clipItems.size * rowH - availableH)
        clipboardOffset = clipboardOffset.coerceIn(0f, maxOffset)

        ime.clipItems.forEachIndexed { i, item ->
            val y = top + i * rowH - clipboardOffset
            if (y + rowH < top || y >= height) return@forEachIndexed

            paint.color = 0xFF22242D.toInt()
            c.drawRoundRect(
                RectF(7f, y + 4f, width - 7f, min(y + rowH - 4f, height.toFloat())),
                8f, 8f, paint
            )

            // Only the preview is limited to two lines. The original text in
            // ClipItem is never truncated and is what gets pasted.
            val textWidth = width - 70f
            paint.color = 0xFFF0F0F0.toInt()
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 21f
            val preview = StaticLayout.Builder
                .obtain(item.text, 0, item.text.length, TextPaint(paint), textWidth.toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(textWidth.toInt().coerceAtLeast(1))
                .setMaxLines(2)
                .build()

            c.save()
            c.translate(18f, y + 11f)
            c.clipRect(0f, 0f, textWidth, rowH - 14f)
            preview.draw(c)
            c.restore()

            drawPinIcon(c, width - 28f, y + rowH / 2f, item.pinned)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawPinIcon(c: Canvas, cx: Float, cy: Float, pinned: Boolean) {
        val color = if (pinned) 0xFFF5F5F5.toInt() else 0xFF777982.toInt()
        paint.color = color
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx - 9f, cy + 12f, cx + 6f, cy - 4f, paint)
        c.drawLine(cx - 5f, cy - 9f, cx + 9f, cy + 5f, paint)
        paint.style = Paint.Style.FILL
        val path = android.graphics.Path().apply {
            moveTo(cx - 11f, cy - 9f)
            lineTo(cx + 7f, cy - 13f)
            lineTo(cx + 12f, cy + 2f)
            lineTo(cx + 2f, cy + 7f)
            close()
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private val emojiCategories = listOf(
        "😀" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕"),
        "👋" to listOf("👋","🤚","🖐️","✋","🖖","👌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","👃","🧠","🫀","🫁","🦷","🦴","👀","👁️","👅","👄"),
        "❤️" to listOf("❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","💌","💋","💯","💢","💥","💫","💦","💨","💤","🔥","✨","⭐","🌟","💫","🎉","🎊","🎈","🎁","🏆","🥇","🥈","🥉"),
        "🐶" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌","🐞","🐜","🕷️","🐢","🐍","🦎","🦂","🐙","🦑","🦀","🐠","🐟","🐡","🦈","🐳","🐋","🐊","🐅","🐆","🦓","🦍","🐘","🦒","🦘","🐪","🐫","🦬","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕","🐈"),
        "🍎" to listOf("🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍔","🍟","🍕","🥪","🥙","🧆","🌮","🌯","🥗","🍿","🍣","🍱","🍜","🍝","🍛","🍚","🍙","🍰","🎂","🍩","🍪","🍫","🍬","🍭","🍮","🍯"),
        "⚽" to listOf("⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🏑","🥍","🏏","⛳","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛷","⛸️","🎿","🏆","🥇","🥈","🥉","🏅","🎖️","🎗️","🎯","🎮","🕹️","🎲","🧩","♟️","🎭","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🎷","🎺","🎸","🪕","🎻","🎪"),
        "🚗" to listOf("🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛵","🏍️","🚲","🛴","🚨","🚔","🚍","🚘","🚖","✈️","🛫","🛬","🚁","🚀","🛸","🚢","⛵","🚤","🛥️","🛳️","🚂","🚆","🚇","🚊","🚉","🚝","🚄","🚅","🚈","🚞","🚋","🚃"),
        "🌍" to listOf("🌍","🌎","🌏","🌐","🗺️","🧭","🏔️","⛰️","🌋","🏕️","🏖️","🏜️","🏝️","🏞️","🏟️","🏛️","🏗️","🏘️","🏠","🏡","🏢","🏥","🏦","🏨","🏪","🏫","🏭","🏰","🗼","🗽","⛪","🕌","🕍","🛕","🕋","⛩️","🌅","🌄","🌇","🌆","🌃","🌌","🌉","🌁"),
        "🐱" to listOf("😺","😸","😹","😻","😼","😽","🙀","😿","😾","🐱","🐈","🐈‍⬛","🦁","🐯","🐅","🐆","🐴","🦄","🦓","🦌","🐮","🐂","🐃","🐄","🐷","🐖","🐗","🐏","🐑","🐐","🐪","🐫","🦙","🦒","🐘","🦏","🦛","🐭","🐹","🐰","🐇","🦔","🦇","🐻","🐨","🐼","🦘","🦡","🦫"),
        "☀️" to listOf("☀️","🌤️","⛅","🌥️","🌦️","🌧️","⛈️","🌩️","🌨️","❄️","☃️","⛄","🌬️","💨","🌪️","🌫️","🌈","☁️","🌙","🌛","🌜","⭐","🌟","✨","⚡","🔥","💧","🌊","🌱","🌲","🌳","🌴","🌵","🌷","🌹","🌺","🌸","🌼","🌻","🌞","🌝"),
        "🔣" to listOf("❤️","💯","✔️","❌","⭕","❗","❓","‼️","⁉️","⚠️","🚫","🔞","♻️","✅","❎","➕","➖","✖️","➗","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔺","🔻","🔷","🔶","🔸","🔹","🔳","🔲","▪️","▫️","◾","◽","◼️","◻️","⬛","⬜","🔊","🔇","🔔","🔕","📌","📍","🔒","🔓","🔑","🔍","💡","⚙️","📱","💻","⌨️","🖥️","📷","🎥","📞","☎️","📩","📧","📁","📂","🗑️","📋","📎","✏️","📝")
    )

    private fun drawEmojiPanel(c: Canvas, top: Float) {
        val panelH = (height - top).coerceAtLeast(1f)
        paint.color = 0xFF3A3B45.toInt()
        c.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)

        val catH = 58f.coerceAtMost(panelH * .15f)
        paint.color = 0xFF272932.toInt()
        c.drawRect(0f, top, width.toFloat(), top + catH, paint)

        val cats = emojiCategories.map { it.first }
        val catW = max(52f, width / 6.5f)
        val start = 8f - emojiCategory * catW
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(31f, catH * .58f)
        cats.forEachIndexed { i, icon ->
            val x = start + i * catW + catW / 2f
            if (x > -catW && x < width + catW) {
                paint.color = if (i == emojiCategory) 0xFFF3F3F3.toInt() else 0xFFB8BAC1.toInt()
                c.drawText(icon, x, top + catH * .66f, paint)
            }
        }

        val list = emojiCategories[emojiCategory].second
        val gridTop = top + catH + 8f
        val cols = 8
        val cellW = width / cols.toFloat()
        val cellH = min(61f, (height - gridTop - 8f) / 5.8f)
        val rows = ((height - gridTop - 8f) / cellH).toInt().coerceAtLeast(1)
        val visible = min(list.size, rows * cols)
        paint.textSize = min(35f, cellH * .62f)
        for (i in 0 until visible) {
            val row = i / cols
            val col = i % cols
            val x = col * cellW + cellW / 2f
            val y = gridTop + row * cellH + cellH * .68f
            c.drawText(list[i], x, y, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    fun isEmojiOpen(): Boolean = emojiOpen

    fun closeEmojiPanel() {
        emojiOpen = false
        diacriticsOpen = false
        invalidate()
    }

    fun resetTransientPanels() {
        emojiOpen = false
        settingsOpen = false
        diacriticsOpen = false
        downKey = null
        backspaceRepeat = false
        removeCallbacks(backspaceRunnable)
        invalidate()
    }

    fun toggleEmojiPanel() {
        emojiOpen = !emojiOpen
        settingsOpen = false
        diacriticsOpen = false
        invalidate()
    }

    private fun drawDiacriticsPopup(c: Canvas) {
        val labels = listOf("َ", "ِ", "ُ", "ً", "ٍ", "ٌ", "ْ", "ّ", "ـ")
        val boxW = width * .92f
        val boxH = min(105f, height * .22f)
        val left = (width - boxW) / 2f
        val top = height * .39f

        paint.color = 0xFF454750.toInt()
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), 14f, 14f, paint)

        paint.color = 0xFFF5F5F5.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(31f, boxH * .38f)

        val cellW = boxW / labels.size
        labels.forEachIndexed { i, label ->
            c.drawText(label, left + cellW * i + cellW / 2f, top + boxH * .62f, paint)
        }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun isPreviewKey(key: Key): Boolean {
        if (key.value.length != 1) return false
        val ch = key.value[0]
        return ch.isLetterOrDigit()
    }

    private fun drawKeyPreview(c: Canvas, key: Key) {
        val label = key.label
        if (label.isEmpty()) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F5F5.toInt()
            textAlign = Paint.Align.CENTER
            textSize = min(34f, key.rect.height() * .62f) * ime.keyTextScale
            typeface = Typeface.DEFAULT
        }

        val horizontalPadding = 20f
        val bubbleW = max(
            key.rect.width() * 1.18f,
            textPaint.measureText(label) + horizontalPadding * 2f
        ).coerceAtMost(width * .30f)
        val bubbleH = max(58f, key.rect.height() * .82f)

        // The bubble is anchored to the pressed key and grows upward.
        // It is allowed to overlap the suggestion bar if the key is in the
        // first row, so it remains above the key instead of moving elsewhere.
        val centerX = key.rect.centerX().coerceIn(bubbleW / 2f + 2f, width - bubbleW / 2f - 2f)
        val bottom = key.rect.top - 4f
        val top = bottom - bubbleH

        val radius = min(16f, bubbleH * .24f)
        val bubble = RectF(
            centerX - bubbleW / 2f,
            top,
            centerX + bubbleW / 2f,
            bottom
        )

        paint.color = 0xFF4A4B54.toInt()
        c.drawRoundRect(bubble, radius, radius, paint)

        // Small pointer aimed at the exact pressed key.
        val pointerHalf = min(13f, bubbleW * .12f)
        val path = android.graphics.Path().apply {
            moveTo(centerX - pointerHalf, bottom - 1f)
            lineTo(centerX + pointerHalf, bottom - 1f)
            lineTo(centerX, bottom + 10f)
            close()
        }
        c.drawPath(path, paint)

        c.drawText(
            label,
            centerX,
            top + bubbleH / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint
        )
    }

    private fun drawKey(c: Canvas, key: Key) {
        paint.color = when (key.type) {
            1 -> 0xFF3A3B43.toInt()
            2 -> 0xFF454750.toInt()
            else -> 0xFF303139.toInt()
        }
        c.drawRoundRect(key.rect, 8f, 8f, paint)
        paint.color = 0xFFF3F3F3.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = min(25f, key.rect.height() * .48f) * ime.keyTextScale
        paint.typeface = Typeface.DEFAULT
        c.drawText(
            key.label,
            key.rect.centerX(),
            key.rect.centerY() - (paint.ascent() + paint.descent()) / 2f,
            paint
        )
        paint.textAlign = Paint.Align.LEFT
    }

    private fun T(a: String, b: String, type: Int = 0) = Triple(a, b, type)

    private fun arabicRows() = if (ime.symbolsMode) listOf(
        listOf(T("!","!"),T("@","@"),T("#","#"),T("$","$"),T("%","%"),T("^","^"),T("&","&"),T("*","*"),T("(","("),T(")",")")),
        listOf(T("-","-"),T("_","_"),T("=","="),T("+","+"),T("[","["),T("]","]"),T("{","{"),T("}","}"),T("<","<"),T(">",">")),
        listOf(T("/","/"),T("\\","\\"),T(": ",":".trim()),T(";",";"),T("\"", "\""),T("'","'"),T("?","?"),T("،","،"),T("؛","؛"),T("ـ","ـ")),
        listOf(T("ABC","TO_LETTERS",1),T("😊","😊",1),T("123","123",1),T("المسافة"," ",2),T(".","."),T("⌫","BACKSPACE",1),T("↵","ENTER",3))
    ) else listOf(
        listOf(T("١","١"),T("٢","٢"),T("٣","٣"),T("٤","٤"),T("٥","٥"),T("٦","٦"),T("٧","٧"),T("٨","٨"),T("٩","٩"),T("٠","٠")),
        listOf(T("ض","ض"),T("ص","ص"),T("ث","ث"),T("ق","ق"),T("ف","ف"),T("غ","غ"),T("ع","ع"),T("ه","ه"),T("خ","خ"),T("ح","ح"),T("ج","ج"),T("د","د")),
        listOf(T("ش","ش"),T("س","س"),T("ي","ي"),T("ب","ب"),T("ل","ل"),T("ا","ا"),T("ت","ت"),T("ن","ن"),T("م","م"),T("ك","ك"),T("ذ","ذ")),
        listOf(T("ء","ء"),T("ؤ","ؤ"),T("ر","ر"),T("ى","ى"),T("ة","ة"),T("و","و"),T("ز","ز"),T("ظ","ظ"),T("ط","ط"),T("-","-"),T("⌫","BACKSPACE",1)),
        listOf(T("123","123",1),T("😊","😊",1),T("،","،"),T("المسافة"," ",2),T(".","."),T("↵","ENTER",3))
    )

    private fun englishRows() = if (ime.symbolsMode) listOf(
        listOf(T("!","!"),T("@","@"),T("#","#"),T("$","$"),T("%","%"),T("^","^"),T("&","&"),T("*","*"),T("(","("),T(")",")")),
        listOf(T("-","-"),T("_","_"),T("=","="),T("+","+"),T("[","["),T("]","]"),T("{","{"),T("}","}"),T("<","<"),T("> ",">".trim())),
        listOf(T("/","/"),T("\\","\\"),T(": ",":".trim()),T(";",";"),T("\"", "\""),T("'","'"),T("?","?"),T("،","،"),T("؛","؛"),T("ـ","ـ")),
        listOf(T("ABC","TO_LETTERS",1),T("😊","😊",1),T("123","123",1),T("space"," ",2),T(".","."),T("⌫","BACKSPACE",1),T("↵","ENTER",3))
    ) else listOf(
        listOf(T("1","1"),T("2","2"),T("3","3"),T("4","4"),T("5","5"),T("6","6"),T("7","7"),T("8","8"),T("9","9"),T("0","0")),
        listOf(T("q","q"),T("w","w"),T("e","e"),T("r","r"),T("t","t"),T("y","y"),T("u","u"),T("i","i"),T("o","o"),T("p","p")),
        listOf(T("a","a"),T("s","s"),T("d","d"),T("f","f"),T("g","g"),T("h","h"),T("j","j"),T("k","k"),T("l","l")),
        listOf(T("⇧","SHIFT",1),T("z","z"),T("x","x"),T("c","c"),T("v","v"),T("b","b"),T("n","n"),T("m","m"),T("⌫","BACKSPACE",1)),
        listOf(T("123","123",1),T("😊","😊",1),T(".","."),T("space"," ",2),T(","," ,".trim()),T("↵","ENTER",3))
    )

    private fun handleSettingsTouch(e: MotionEvent, panelTop: Float): Boolean {
        val panelW = min(width * .86f, 560f)
        val panelH = min(height * .48f, 290f)
        val left = 10f
        val top = panelTop + 8f
        val right = left + panelW
        val bottom = min(height - 8f, top + panelH)
        val sliderLeft = left + 24f
        val sliderRight = right - 24f

        if (e.actionMasked == MotionEvent.ACTION_DOWN &&
            (e.x !in left..right || e.y !in top..bottom)) {
            settingsOpen = false
            settingsDrag = 0
            invalidate()
            return true
        }

        fun updateSlider(which: Int, x: Float) {
            val f = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
            if (which == 1) {
                ime.setKeyboardScale(0.35f + f * (0.95f - 0.35f))
            } else {
                // Expanded range so the highest setting visibly increases key labels.
                ime.setKeyTextScale(0.75f + f * (2.20f - 0.75f))
            }
            invalidate()
        }

        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            val y1 = top + 90f
            val y2 = top + 173f
            val d1 = abs(e.y - y1)
            val d2 = abs(e.y - y2)
            if (settingsDrag == 0) {
                if (d1 < 36f && e.x in sliderLeft..sliderRight) settingsDrag = 1
                else if (d2 < 36f && e.x in sliderLeft..sliderRight) settingsDrag = 2
            }
            if (settingsDrag != 0) updateSlider(settingsDrag, e.x)
            return true
        }

        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            settingsDrag = 0
            return true
        }
        return true
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN && emojiOpen) {
            closeEmojiPanel()
            return true
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val contentTop = 0f
        val contentH = height.toFloat()
        val suggestionH = min(76f, contentH * 0.18f)
        val suggestionTop = contentTop

        if (settingsOpen && !ime.showClipboard) {
            if (handleSettingsTouch(e, contentTop + suggestionH)) return true
        }

        if (emojiOpen) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) return true
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                val panelTop = contentTop + suggestionH
                val panelH = height - panelTop
                val catH = 58f.coerceAtMost(panelH * .15f)
                if (e.y in panelTop..(panelTop + catH)) {
                    val catW = max(52f, width / 6.5f)
                    val idx = ((e.x - 8f + emojiCategory * catW) / catW).toInt()
                    if (idx in emojiCategories.indices) {
                        emojiCategory = idx
                        invalidate()
                    }
                    return true
                }
                val list = emojiCategories[emojiCategory].second
                val gridTop = panelTop + catH + 8f
                val cellW = width / 8f
                val cellH = min(61f, (height - gridTop - 8f) / 5.8f)
                val col = (e.x / cellW).toInt().coerceIn(0, 7)
                val row = ((e.y - gridTop) / cellH).toInt()
                val index = row * 8 + col
                if (index in list.indices && e.y >= gridTop) {
                    ime.commitText(list[index])
                }
                return true
            }
        }

        if (diacriticsOpen) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> return true
                MotionEvent.ACTION_UP -> {
                    val labels = listOf("َ","ِ","ُ","ً","ٍ","ٌ","ْ","ّ","ـ")
                    val boxW = width * .92f
                    val left = (width - boxW) / 2f
                    val top = height * .39f
                    val boxH = min(105f, height * .22f)
                    if (e.x in left..(left + boxW) && e.y in top..(top + boxH)) {
                        val index = ((e.x - left) / (boxW / labels.size)).toInt().coerceIn(0, labels.lastIndex)
                        ime.commitText(labels[index])
                    }
                    diacriticsOpen = false
                    invalidate()
                    return true
                }
            }
        }

        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.x
            downY = e.y
            longPress = false

            if (e.y < suggestionTop || e.y >= contentTop + contentH) {
                downKey = null
                if (ime.showClipboard) {
                    lastMoveY = e.y
                    scrollingClipboard = false
                }
                return true
            }

            if (ime.showClipboard) {
                downKey = null
                lastMoveY = e.y
                clipboardDownX = e.x
                clipboardDownY = e.y
                clipboardSwipeHandled = false
                scrollingClipboard = false
                val rowH = 88f
                val index = ((e.y - (contentTop + suggestionH) + clipboardOffset) / rowH).toInt()
                clipboardDownIndex = index
                clipboardDownItem = ime.clipItems.getOrNull(index)
                return true
            }

            downKey = keys.firstOrNull { it.rect.contains(e.x, e.y) }
            // Show the key preview immediately on press.
            invalidate()

            if (downKey?.value == "BACKSPACE") {
                postDelayed({
                    if (downKey?.value == "BACKSPACE") {
                        longPress = true
                        backspaceRepeat = true
                        ime.deletePreviousWord()
                        postDelayed(backspaceRunnable, repeatDeleteMs)
                        invalidate()
                    }
                }, longPressMs)
            }

            if (downKey?.value == "." && ime.arabic) {
                postDelayed({
                    if (downKey?.value == "." && !longPress) {
                        longPress = true
                        diacriticsOpen = true
                        invalidate()
                    }
                }, longPressMs)
            }
            return true
        }

        if (e.actionMasked == MotionEvent.ACTION_MOVE && ime.showClipboard) {
            val dx = e.x - clipboardDownX
            val dy = e.y - clipboardDownY
            val item = clipboardDownItem
            if (!clipboardSwipeHandled && item != null && !item.pinned && dx > 70f && dx > abs(dy) * 1.2f) {
                clipboardSwipeHandled = true
                ime.deleteClipboardItem(item)
                clipboardDownItem = null
                invalidate()
                return true
            }
            if (!clipboardSwipeHandled && abs(dy) > 1f && abs(dy) > abs(dx) * 0.7f) {
                val delta = e.y - lastMoveY
                clipboardOffset = (clipboardOffset - delta).coerceAtLeast(0f)
                lastMoveY = e.y
                scrollingClipboard = true
                invalidate()
            }
            return true
        }

        if (e.actionMasked == MotionEvent.ACTION_UP) {
            backspaceRepeat = false
            removeCallbacks(backspaceRunnable)
            if (e.y >= suggestionTop && e.y < suggestionTop + suggestionH) {
                if (!ime.showClipboard) {
                    val each = width / 5f
                    when {
                        e.x < each -> ime.openClipboard()
                        e.x < each * 2f -> {
                            settingsOpen = !settingsOpen
                            invalidate()
                        }
                        else -> {
                            val idx = (e.x / each).toInt() - 2
                            if (idx in ime.currentSuggestions.indices) {
                                ime.commitSuggestion(ime.currentSuggestions[idx])
                            }
                        }
                    }
                } else {
                    ime.closeClipboard()
                }
                return true
            }

            if (ime.showClipboard) {
                if (clipboardSwipeHandled || scrollingClipboard) {
                    clipboardDownItem = null
                    clipboardSwipeHandled = false
                    scrollingClipboard = false
                    return true
                }
                val item = clipboardDownItem
                if (item != null) {
                    if (e.x >= width - 72f) {
                        ime.togglePin(item)
                    } else {
                        ime.useClipboardItem(item)
                    }
                }
                clipboardDownItem = null
                return true
            }

            val key = downKey
            if (key != null && !longPress) {
                if (key.value == " ") {
                    val dx = e.x - downX
                    if (abs(dx) >= 45f) ime.toggleLanguage()
                    else ime.commitText(" ")
                } else {
                    ime.handleKey(key.value)
                }
            }
            downKey = null
            invalidate()
            return true
        }

        if (e.actionMasked == MotionEvent.ACTION_CANCEL) {
            backspaceRepeat = false
            removeCallbacks(backspaceRunnable)
            downKey = null
            settingsDrag = 0
            clipboardDownItem = null
            clipboardSwipeHandled = false
            scrollingClipboard = false
            return true
        }
        return true
    }
}
