package com.keyboards10

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.WindowManager

class KeyboardsImeService : InputMethodService() {
    private lateinit var clipboard: ClipboardManager
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { captureClipboard() }

    lateinit var keyboard: KeyboardView
        private set
    lateinit var db: ClipboardDb
        private set
    lateinit var suggestions: SuggestionEngine
        private set

    private val settings by lazy { getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    var arabic = true
        private set
    var symbolsMode = false
        private set
    var shift = false
        private set
    var showClipboard = false
        private set
    var currentSuggestions: List<String> = emptyList()
        private set
    var clipItems: List<ClipItem> = emptyList()
        private set

    var keyboardScale: Float = 0.72f
        private set
    var keyTextScale: Float = 1.0f
        private set

    override fun onCreate() {
        super.onCreate()
        keyboardScale = settings.getFloat("keyboard_scale", 0.72f).coerceIn(0.35f, 0.95f)
        keyTextScale = settings.getFloat("key_text_scale", 1.0f).coerceIn(0.75f, 2.20f)

        db = ClipboardDb(this)
        suggestions = SuggestionEngine(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipboardListener)
        refreshClipboard()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigureWindow(
        win: android.view.Window,
        isFullscreen: Boolean,
        isCandidatesOnly: Boolean
    ) {
        super.onConfigureWindow(win, false, isCandidatesOnly)
        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateInputView(): View {
        keyboard = KeyboardView(this, this)
        keyboard.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            keyboardHeightPx()
        )
        refreshSuggestions()
        return keyboard
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        updateFullscreenMode()
        showClipboard = false
        if (::keyboard.isInitialized) keyboard.resetTransientPanels()
        refreshSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        showClipboard = false
        if (::keyboard.isInitialized) keyboard.resetTransientPanels()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && ::keyboard.isInitialized && keyboard.isEmojiOpen()) {
            keyboard.closeEmojiPanel()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::clipboard.isInitialized) clipboard.removePrimaryClipChangedListener(clipboardListener)
        super.onDestroy()
    }

    fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (text.any { it.isWhitespace() }) learnFromCursorContext()
        refreshSuggestions()
    }

    fun commitSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(240, 0)?.toString().orEmpty()
        val currentWord = before.takeLastWhile { !it.isWhitespace() }

        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
            ic.commitText(text, 1)
        } else {
            val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            ic.commitText(prefix + text, 1)
        }

        suggestions.learn(text, arabic)
        learnFromCursorContext()
        refreshSuggestions()
    }

    fun handleKey(value: String) {
        val ic = currentInputConnection ?: return
        when (value) {
            "BACKSPACE" -> deleteOneCharacter()
            "ENTER" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null &&
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
                learnFromCursorContext()
                refreshSuggestions()
            }
            "123" -> {
                symbolsMode = !symbolsMode
                keyboard.invalidate()
            }
            "TO_LETTERS" -> {
                symbolsMode = false
                keyboard.invalidate()
            }
            "SHIFT" -> {
                shift = !shift
                keyboard.invalidate()
            }
            "😊" -> keyboard.toggleEmojiPanel()
            else -> {
                ic.commitText(value, 1)
                if (value.length == 1 && value[0].isWhitespace()) learnFromCursorContext()
                refreshSuggestions()
            }
        }
    }

    fun deleteOneCharacter() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
        refreshSuggestions()
    }

    /** Deletes the previous word, including its separating whitespace. */
    fun deletePreviousWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        if (before.isEmpty()) return

        var end = before.length
        while (end > 0 && before[end - 1].isWhitespace()) end--
        if (end == 0) {
            ic.deleteSurroundingText(1, 0)
            refreshSuggestions()
            return
        }

        var start = end
        while (start > 0 && !before[start - 1].isWhitespace()) start--
        ic.deleteSurroundingText(before.length - start, 0)
        refreshSuggestions()
    }

    fun toggleLanguage() {
        arabic = !arabic
        symbolsMode = false
        shift = false
        showClipboard = false
        keyboard.invalidate()
        refreshSuggestions()
    }

    fun openClipboard() {
        showClipboard = true
        refreshClipboard()
        keyboard.invalidate()
    }

    fun useClipboardItem(item: ClipItem) {
        commitText(item.text)
        showClipboard = false
        keyboard.invalidate()
    }

    fun deleteClipboardItem(item: ClipItem) {
        if (item.pinned) return
        db.delete(item.id)
        refreshClipboard()
    }

    fun closeClipboard() {
        showClipboard = false
        keyboard.invalidate()
        refreshSuggestions()
    }

    fun togglePin(item: ClipItem) {
        db.setPinned(item.id, !item.pinned)
        refreshClipboard()
    }

    private fun keyboardHeightPx(): Int {
        val screenH = resources.displayMetrics.heightPixels
        val f = ((keyboardScale - 0.35f) / (0.95f - 0.35f)).coerceIn(0f, 1f)
        return (screenH * (0.28f + f * 0.27f)).toInt()
    }

    fun setKeyboardScale(value: Float) {
        keyboardScale = value.coerceIn(0.35f, 0.95f)
        settings.edit().putFloat("keyboard_scale", keyboardScale).apply()

        if (!::keyboard.isInitialized) return

        val targetHeight = keyboardHeightPx()

        keyboard.post {
            val imeWindow = window?.window ?: return@post

            imeWindow.setGravity(android.view.Gravity.BOTTOM)
            imeWindow.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeight
            )

            val lp = keyboard.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeight
            )
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = targetHeight
            keyboard.layoutParams = lp
            keyboard.requestLayout()
            keyboard.invalidate()
        }
    }

    fun setKeyTextScale(value: Float) {
        keyTextScale = value.coerceIn(0.75f, 2.20f)
        settings.edit().putFloat("key_text_scale", keyTextScale).apply()
        if (::keyboard.isInitialized) keyboard.invalidate()
    }

    private fun captureClipboard() {
        val clip: ClipData = clipboard.primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isNotEmpty()) {
            db.add(text)
            refreshClipboard()
        }
    }

    private fun refreshClipboard() {
        if (::db.isInitialized) {
            clipItems = db.all()
            if (::keyboard.isInitialized) keyboard.invalidate()
        }
    }

    private fun learnFromCursorContext() {
        if (!::suggestions.isInitialized) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
        suggestions.learnContext(before, arabic)
    }

    fun refreshSuggestions() {
        if (!::keyboard.isInitialized || !::suggestions.isInitialized) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(240, 0)?.toString().orEmpty()
        currentSuggestions = suggestions.suggestions(before, arabic)
        keyboard.invalidate()
    }
}
