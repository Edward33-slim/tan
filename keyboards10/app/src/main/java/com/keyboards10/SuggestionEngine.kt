package com.keyboards10

import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * Offline contextual predictor.
 *
 * It is intentionally local: no text leaves the device.  The engine combines
 * a built-in frequency lexicon, current-word completions, learned word counts,
 * and learned 2/3-word context.  It is not Microsoft's proprietary SwiftKey
 * engine, but it follows the same high-level interaction model: complete the
 * current word, then predict the next word from the sentence context.
 */
class SuggestionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)

    private val ar = listOf(
        "أنا","انت","أنت","انتي","أنتِ","نحن","هو","هي","هم","هذا","هذه","هنا","هناك",
        "من","ما","ماذا","متى","أين","كيف","لماذا","الذي","التي","الذين","كل","بعض",
        "مرحبا","مرحباً","السلام","عليكم","وعليكم","شكرا","شكرًا","العفو","آسف","عفوا",
        "أريد","اريد","أحتاج","احتاج","أستطيع","استطيع","يمكن","ممكن","لازم","يجب","أحب","احب",
        "اليوم","غدا","غدًا","أمس","الآن","الان","بعد","قبل","دائما","دائمًا","أحيانا","أحيانًا",
        "البيت","العمل","السوق","المدرسة","الجامعة","المستشفى","الشارع","المطعم","المكتب",
        "التطبيق","الكيبورد","لوحة","المفاتيح","كلمة","كلمات","نص","رسالة","رسائل","مقالة",
        "برنامج","مشروع","رابط","ملف","صورة","فيديو","مشكلة","حل","طريقة","خطوة","إعدادات",
        "جديد","جديدة","قديم","جميل","جميلة","تمام","جيد","جيدة","ممتاز","صحيح","خطأ",
        "نعم","لا","لكن","لأن","لذلك","إذا","ثم","مع","بدون","على","في","منذ","حتى","عن",
        "و","أو","ثم","لكن","أيضا","أيضًا","فقط","جدا","جدًا","تقريبًا","ربما","أكيد","بالتأكيد",
        "صباح","مساء","ليل","وقت","ساعة","دقيقة","يوم","أسبوع","شهر","سنة","بكرة","الآن",
        "تعال","تعالي","اذهب","روح","شوف","انظر","قل","أرسل","ارسل","افتح","اغلق","اكتب","اقرأ",
        "سوف","راح","سأ","سوف يكون","كان","كانت","يكون","تكون","صار","صارت","عندي","عندك","لدينا",
        "شكرا لك","شكراً لك","بارك الله فيك","إن شاء الله","ان شاء الله","الله يبارك فيك"
    )

    private val en = listOf(
        "the","and","you","are","this","that","hello","thanks","thank","please","from","with","for",
        "have","has","had","will","would","can","could","want","need","what","when","where","why","how",
        "today","tomorrow","yesterday","now","later","before","after","always","sometimes","maybe","really",
        "home","work","school","office","market","restaurant","hospital","street","university",
        "app","keyboard","message","messages","text","code","download","android","project","file","link",
        "photo","image","video","settings","problem","solution","way","step","new","good","great","nice",
        "yes","no","but","because","so","if","then","with","without","about","only","also","very","sure",
        "morning","afternoon","evening","night","time","hour","minute","day","week","month","year",
        "come","go","look","see","send","open","close","write","read","make","use","help","try",
        "will","going","just","already","still","first","last","next","please","okay","ok","right","wrong"
    )

    private val arContext = listOf(
        "السلام عليكم","وعليكم السلام","صباح الخير","مساء الخير","شكرا لك","شكراً لك","العفو يا صديقي",
        "كيف حالك","كيف حالكم","أنا بخير","انا بخير","أريد أن","اريد ان","أحتاج إلى","احتاج الى",
        "ممكن تساعدني","هل يمكنك","هل ممكن","ما هو","ما هي","أين أنت","اين انت","ماذا تريد",
        "إن شاء الله","ان شاء الله","الله يبارك فيك","لا مشكلة","لا بأس","كل شيء","كل شي",
        "أنا في البيت","انا في البيت","أنا في العمل","انا في العمل","أرسل لي","ارسل لي","أخبرني عن",
        "افتح التطبيق","افتح الكيبورد","لوحة المفاتيح","إعدادات الكيبورد","شكرا جزيلا","شكراً جزيلاً",
        "اليوم إن شاء الله","غدا إن شاء الله","سوف يكون","راح يكون","أريد أن أكتب","ممكن ترسل لي"
    )

    private val enContext = listOf(
        "hello there","good morning","good evening","how are you","I am fine","thank you","thanks for",
        "please help me","can you help","could you please","what is the","what are the","where are you",
        "I want to","I need to","I would like to","let me know","send me the","open the app","open the keyboard",
        "keyboard settings","see you soon","talk to you","have a good","have a great","no problem","that is",
        "this is","there is","there are","I am going","I will be","we can","we need","I think","I know",
        "thank you very much","good night","see you tomorrow","right now","for the next"
    )

    private data class Scored(val word: String, val score: Int)

    fun suggestions(textBeforeCursor: String, arabic: Boolean): List<String> {
        val tokens = tokenize(textBeforeCursor)
        val current = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
        val base = if (arabic) ar else en
        val normalizedCurrent = if (arabic) current else current.lowercase(Locale.ROOT)

        return if (current.isNotEmpty()) {
            currentWordCompletions(normalizedCurrent, base, arabic)
        } else {
            nextWordPredictions(tokens, base, arabic)
        }
    }

    fun learn(word: String, arabic: Boolean) {
        val clean = word.trim()
        if (clean.isEmpty() || clean.length > 100 || clean.any { it.isWhitespace() }) return
        val lang = if (arabic) "ar" else "en"
        val key = "\$lang:word:\${clean.lowercase(Locale.ROOT)}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun learnContext(textBeforeCursor: String, arabic: Boolean) {
        val tokens = tokenize(textBeforeCursor)
        if (tokens.isEmpty()) return
        val lang = if (arabic) "ar" else "en"
        val clean = tokens.takeLast(8)
        val editor = prefs.edit()
        clean.takeLast(3).forEach { token ->
            val key = "\$lang:word:\${token.lowercase(Locale.ROOT)}"
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }
        if (clean.size >= 2) {
            val a = clean[clean.lastIndex - 1].lowercase(Locale.ROOT)
            val b = clean.last().lowercase(Locale.ROOT)
            val key = "\$lang:bi:\$a|\$b"
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }
        if (clean.size >= 3) {
            val a = clean[clean.lastIndex - 2].lowercase(Locale.ROOT)
            val b = clean[clean.lastIndex - 1].lowercase(Locale.ROOT)
            val d = clean.last().lowercase(Locale.ROOT)
            val key = "\$lang:tri:\$a|\$b|\$d"
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }
        editor.apply()
    }

    private fun currentWordCompletions(prefix: String, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val all = LinkedHashSet<String>()
        all.addAll(base)
        prefs.all.keys.filter { it.startsWith("\$lang:word:") }.forEach {
            all.add(it.removePrefix("\$lang:word:"))
        }

        val exact = ArrayList<Scored>()
        val fuzzy = ArrayList<Scored>()
        for (candidateRaw in all) {
            val candidate = if (arabic) candidateRaw else candidateRaw.lowercase(Locale.ROOT)
            val count = prefs.getInt("\$lang:word:\${candidate.lowercase(Locale.ROOT)}", 0)
            if (candidate.startsWith(prefix, ignoreCase = !arabic)) {
                val baseScore = if (candidate.equals(prefix, ignoreCase = !arabic)) 10_000 else 1_000
                exact += Scored(candidateRaw, baseScore + count * 30 - candidate.length)
            } else if (prefix.length >= 3) {
                val distance = levenshtein(prefix, candidate.take(max(prefix.length, 1)))
                if (distance <= 1) fuzzy += Scored(candidateRaw, 500 + count * 20 - distance * 50 - candidate.length)
            }
        }

        return (exact.sortedByDescending { it.score } + fuzzy.sortedByDescending { it.score })
            .map { it.word }
            .distinctBy { if (arabic) it else it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun nextWordPredictions(tokens: List<String>, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { it.lowercase(Locale.ROOT) }
        val last = normalized.lastOrNull()
        val previous = normalized.getOrNull(normalized.lastIndex - 1)
        val corpus = if (arabic) arContext else enContext
        val score = LinkedHashMap<String, Int>()

        corpus.forEach { phrase ->
            val parts = tokenize(phrase)
            if (parts.size >= 2 && last != null) {
                for (i in 0 until parts.lastIndex) {
                    if (parts[i].equals(last, ignoreCase = true)) {
                        val next = parts[i + 1]
                        score[next] = (score[next] ?: 0) + 120
                    }
                }
            }
            if (parts.size >= 3 && previous != null && last != null) {
                for (i in 0 until parts.size - 2) {
                    if (parts[i].equals(previous, true) && parts[i + 1].equals(last, true)) {
                        val next = parts[i + 2]
                        score[next] = (score[next] ?: 0) + 220
                    }
                }
            }
        }

        if (previous != null && last != null) {
            prefs.all.keys.filter { it.startsWith("\$lang:tri:\$previous|\$last|") }.forEach { key ->
                val next = key.substringAfterLast('|')
                score[next] = (score[next] ?: 0) + 500 + prefs.getInt(key, 0) * 50
            }
        }
        if (last != null) {
            prefs.all.keys.filter { it.startsWith("\$lang:bi:\$last|") }.forEach { key ->
                val next = key.substringAfterLast('|')
                score[next] = (score[next] ?: 0) + 300 + prefs.getInt(key, 0) * 40
            }
        }

        prefs.all.keys.filter { it.startsWith("\$lang:word:") }.forEach { key ->
            val word = key.removePrefix("\$lang:word:")
            score[word] = (score[word] ?: 0) + prefs.getInt(key, 0) * 2
        }
        base.forEachIndexed { index, word ->
            val normalizedWord = word.lowercase(Locale.ROOT)
            score[normalizedWord] = (score[normalizedWord] ?: 0) + max(1, 200 - index)
        }

        return score.entries
            .sortedByDescending { it.value }
            .map { entry -> restoreCase(entry.key, base, arabic) }
            .distinctBy { if (arabic) it else it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun restoreCase(word: String, base: List<String>, arabic: Boolean): String {
        if (arabic) return base.firstOrNull { it.equals(word, true) } ?: word
        return base.firstOrNull { it.equals(word, true) } ?: word
    }

    private fun tokenize(text: String): List<String> =
        Regex("[\\p{L}\\p{M}\\p{Nd}']+").findAll(text).map { it.value }.toList()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i].equals(b[j], ignoreCase = true)) 0 else 1
                cur[j + 1] = minOf(
                    cur[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost
                )
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
