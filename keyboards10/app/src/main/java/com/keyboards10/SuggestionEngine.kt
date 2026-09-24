package com.keyboards10

import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * Local prediction engine inspired by modern prediction keyboards:
 * current-word completion/correction, next-word prediction, and
 * on-device personalization.
 */
class SuggestionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)

    private val ar = listOf(
        "أنا","أنت","أنتِ","أنتي","نحن","هو","هي","هم","هذا","هذه","هنا","هناك","من","ما","ماذا","متى","أين","كيف","لماذا",
        "الذي","التي","الذين","كل","بعض","أي","أيضا","أيضًا","فقط","جدا","جدًا","تقريبًا","ربما","أكيد","بالتأكيد",
        "مرحبا","مرحباً","السلام","عليكم","وعليكم","شكرا","شكرًا","شكراً","العفو","آسف","عفوا","أهلا","أهلاً","وسهلاً",
        "أريد","اريد","أحتاج","احتاج","أستطيع","استطيع","يمكن","ممكن","لازم","يجب","أحب","احب","أعرف","اعرف","أفهم","افهم",
        "اليوم","غدا","غدًا","أمس","الآن","الان","بعد","قبل","دائما","دائمًا","أحيانا","أحيانًا","بكرة","غداً",
        "البيت","العمل","السوق","المدرسة","الجامعة","المستشفى","الشارع","المطعم","المكتب","السيارة","الهاتف",
        "التطبيق","الكيبورد","لوحة","المفاتيح","كلمة","كلمات","نص","رسالة","رسائل","مقالة","برنامج","مشروع","رابط","ملف",
        "صورة","فيديو","مشكلة","حل","طريقة","خطوة","إعدادات","حجم","شريط","اقتراحات","توقع","تنبؤ","كلام","شيء","شي",
        "جديد","جديدة","قديم","جميل","جميلة","تمام","جيد","جيدة","ممتاز","صحيح","خطأ","نعم","لا","لكن","لأن","لذلك","إذا","ثم",
        "مع","بدون","على","في","منذ","حتى","عن","و","أو","وقت","ساعة","دقيقة","يوم","أسبوع","شهر","سنة",
        "تعال","تعالي","اذهب","روح","شوف","انظر","قل","أرسل","ارسل","افتح","اغلق","أغلق","اكتب","اقرأ","ساعدني","ساعد",
        "تستطيع","تقدر","أخبرني","اخبرني","أرسل لي","ارسل لي","أعطني","اعطني","من فضلك","لو سمحت",
        "إن شاء الله","ان شاء الله","الله","شكرا لك","شكراً لك","بارك الله فيك","كل شيء","كل شي","لا مشكلة","لا بأس",
        "هل","هل يمكن","هل تستطيع","هل تقدر","ما هو","ما هي","كيف يمكنني","كيف أستطيع","ماذا تريد","ماذا تفعل",
        "أنا بخير","انا بخير","الحمد لله","صباح الخير","مساء الخير","ليلة سعيدة","مرحبا بك","مرحبا بكم","أهلا وسهلا",
        "ممكن تساعدني","ممكن ترسل","ممكن ترسل لي","أريد أن","اريد ان","أحتاج إلى","احتاج الى","بعد ذلك","قبل ذلك",
        "في الوقت الحالي","في نفس الوقت","على كل حال","شكرا جزيلا","شكراً جزيلاً"
    )

    private val en = listOf(
        "i","you","he","she","we","they","it","this","that","these","those","there","here","the","a","an","and","or","but",
        "if","then","so","because","for","from","with","without","about","into","on","in","at","to","of","is","are","was","were",
        "have","has","had","will","would","can","could","should","want","need","what","when","where","why","who",
        "hello","thanks","thank","please","sorry","welcome","today","tomorrow","yesterday","now","later","before","after",
        "always","sometimes","maybe","really","just","already","still","more","much","many","some","any","all","only","also",
        "very","sure","home","work","school","office","market","restaurant","hospital","street","university","car","phone",
        "app","keyboard","message","messages","text","code","download","android","project","file","link","photo","image",
        "video","settings","problem","solution","way","step","new","good","great","nice","right","wrong","yes","no","okay","ok",
        "morning","afternoon","evening","night","time","hour","minute","day","week","month","year","come","go","look","see",
        "send","open","close","write","read","make","use","help","try","going","first","last","next","best","working","works"
    )

    private val arNext = mapOf(
        "أنا" to listOf("أريد","بخير","في","من","لا","أحب"),
        "انا" to listOf("اريد","بخير","في","من","لا","احب"),
        "أريد" to listOf("أن","ال","من","شيء","هذا","مساعدة"),
        "اريد" to listOf("ان","ال","من","شي","هذا","مساعدة"),
        "أحتاج" to listOf("إلى","مساعدة","من","هذا","شيء"),
        "احتاج" to listOf("الى","مساعدة","من","هذا","شي"),
        "كيف" to listOf("يمكنني","أستطيع","حالك","تعمل","ذلك","أفعل"),
        "ماذا" to listOf("تفعل","تريد","هذا","هي","هو","الآن"),
        "هل" to listOf("يمكن","تستطيع","تقدر","هذا","هناك","ممكن"),
        "من" to listOf("فضلك","أجل","الممكن","البيت","العمل"),
        "شكرا" to listOf("لك","جزيلا","على","وأيضا"),
        "شكراً" to listOf("لك","جزيلاً","على","وأيضاً"),
        "السلام" to listOf("عليكم"),
        "صباح" to listOf("الخير"),
        "مساء" to listOf("الخير"),
        "إن" to listOf("شاء","كان","أردت"),
        "ان" to listOf("شاء","كان","اردت"),
        "لا" to listOf("مشكلة","بأس","أعرف","أستطيع","يمكن"),
        "ممكن" to listOf("تساعدني","ترسل","ترسل لي","تشرح","تقول"),
        "لو" to listOf("سمحت"),
        "الله" to listOf("يبارك","يحفظك","خير"),
        "كل" to listOf("شيء","شي","يوم","عام"),
        "بعد" to listOf("ذلك","قليل","الوقت"),
        "قبل" to listOf("ذلك","أن","ما"),
        "في" to listOf("البيت","العمل","هذا","الوقت","المستقبل"),
        "على" to listOf("كل","الأقل"),
        "هذا" to listOf("هو","شيء","الذي","المشروع"),
        "هذه" to listOf("هي","الكلمة","المشكلة","الطريقة")
    )

    private val enNext = mapOf(
        "i" to listOf("am","want","need","will","can","think","know"),
        "you" to listOf("are","can","will","have","want","need","should"),
        "we" to listOf("are","can","will","need","have","should"),
        "they" to listOf("are","will","can","have"),
        "the" to listOf("best","next","first","same","way","keyboard","app"),
        "a" to listOf("new","good","great","little","lot","way"),
        "an" to listOf("example","app","idea"),
        "what" to listOf("is","are","do","do you","about"),
        "how" to listOf("are","can","do","do i","to"),
        "where" to listOf("are","is","can","do"),
        "when" to listOf("you","will","can","is","are"),
        "can" to listOf("you","i","we","be","help"),
        "could" to listOf("you","you please","be","i"),
        "please" to listOf("help","send","open","check","wait"),
        "thank" to listOf("you"),
        "thanks" to listOf("for","you"),
        "good" to listOf("morning","evening","night","to","idea"),
        "right" to listOf("now","away","here"),
        "send" to listOf("me","the","it","this"),
        "open" to listOf("the","it","this","app"),
        "help" to listOf("me","with","you"),
        "i am" to listOf("going","fine","here","ready"),
        "i want" to listOf("to","the","a","this"),
        "i need" to listOf("to","a","the","help"),
        "you can" to listOf("use","try","send","open"),
        "no" to listOf("problem","thanks","one","more")
    )

    private data class Scored(val word: String, val score: Int)

    fun suggestions(textBeforeCursor: String, arabic: Boolean): List<String> {
        val tokens = tokenize(textBeforeCursor)
        val current = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
        val base = if (arabic) ar else en
        return if (current.isNotEmpty()) {
            currentWordCompletions(current, base, arabic)
        } else {
            nextWordPredictions(tokens, base, arabic)
        }
    }

    fun learn(word: String, arabic: Boolean) {
        val clean = word.trim()
        if (clean.isEmpty() || clean.length > 80 || clean.any { it.isWhitespace() }) return
        val lang = if (arabic) "ar" else "en"
        val normalized = normalize(clean, arabic)
        val key = lang + ":word:" + normalized
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun learnContext(textBeforeCursor: String, arabic: Boolean) {
        val tokens = tokenize(textBeforeCursor).takeLast(14)
        if (tokens.isEmpty()) return

        val lang = if (arabic) "ar" else "en"
        val editor = prefs.edit()
        val normalized = tokens.map { normalize(it, arabic) }

        normalized.forEach { token ->
            val key = lang + ":word:" + token
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        for (i in 0 until normalized.lastIndex) {
            val key = lang + ":bi:" + normalized[i] + "|" + normalized[i + 1]
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        for (i in 0 until normalized.size - 2) {
            val key = lang + ":tri:" + normalized[i] + "|" + normalized[i + 1] + "|" + normalized[i + 2]
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        editor.apply()
    }

    private fun currentWordCompletions(prefixRaw: String, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val prefix = normalize(prefixRaw, arabic)
        val all = LinkedHashSet<String>()
        all.addAll(base)

        prefs.all.keys
            .filter { it.startsWith(lang + ":word:") }
            .forEach { all.add(it.removePrefix(lang + ":word:")) }

        val exact = ArrayList<Scored>()
        val fuzzy = ArrayList<Scored>()

        for (candidateRaw in all) {
            val candidate = normalize(candidateRaw, arabic)
            val freq = prefs.getInt(lang + ":word:" + candidate, 0)

            if (candidate.startsWith(prefix)) {
                val exactness = if (candidate == prefix) 7000 else 2600
                val prefixQuality = max(0, 160 - (candidate.length - prefix.length) * 9)
                exact += Scored(displayWord(candidateRaw, base, arabic), exactness + freq * 120 + prefixQuality)
            } else if (prefix.length >= 2) {
                val probe = candidate.take(max(prefix.length, 1))
                val distance = levenshtein(prefix, probe)
                if (distance <= 2) {
                    fuzzy += Scored(displayWord(candidateRaw, base, arabic), 1250 + freq * 70 - distance * 260 - candidate.length)
                }
            }
        }

        exact += Scored(prefixRaw, 6200)

        return (exact.sortedByDescending { it.score } + fuzzy.sortedByDescending { it.score })
            .map { it.word }
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it, arabic) }
            .take(3)
    }

    private fun nextWordPredictions(tokens: List<String>, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { normalize(it, arabic) }
        val last = normalized.lastOrNull()
        val previous = normalized.getOrNull(normalized.lastIndex - 1)
        val score = LinkedHashMap<String, Int>()

        fun add(wordRaw: String, amount: Int) {
            val word = wordRaw.trim()
            if (word.isEmpty() || word.any { it.isWhitespace() }) return
            val key = normalize(word, arabic)
            score[key] = (score[key] ?: 0) + amount
        }

        if (previous != null && last != null) {
            val prefix = lang + ":tri:" + previous + "|" + last + "|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 1600 + prefs.getInt(key, 0) * 160)
            }
        }

        if (last != null) {
            val prefix = lang + ":bi:" + last + "|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 1100 + prefs.getInt(key, 0) * 110)
            }

            val map = if (arabic) arNext else enNext
            map[last]?.forEachIndexed { index, word ->
                add(word, 900 - index * 70)
            }
        }

        prefs.all.keys.filter { it.startsWith(lang + ":word:") }.forEach { key ->
            val word = key.removePrefix(lang + ":word:")
            add(word, 35 + prefs.getInt(key, 0) * 18)
        }

        base.forEachIndexed { index, word ->
            add(word, 170 - index.coerceAtMost(120))
        }

        return score.entries
            .sortedByDescending { it.value }
            .map { displayWord(it.key, base, arabic) }
            .distinctBy { normalize(it, arabic) }
            .take(3)
    }

    private fun displayWord(word: String, base: List<String>, arabic: Boolean): String {
        base.firstOrNull { normalize(it, arabic) == normalize(word, arabic) }?.let { return it }
        return word
    }

    private fun normalize(word: String, arabic: Boolean): String {
        var value = word.trim()
        if (!arabic) value = value.lowercase(Locale.ROOT)
        return value
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            .replace('ى', 'ي')
    }

    private fun tokenize(text: String): List<String> =
        Regex("[\\\\p{L}\\\\p{M}\\\\p{Nd}']+")
            .findAll(text)
            .map { it.value }
            .toList()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i].equals(b[j], ignoreCase = true)) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }
}
