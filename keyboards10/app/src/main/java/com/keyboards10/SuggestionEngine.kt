package com.keyboards10

import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * Local prediction engine modeled after the documented behavior of modern
 * predictive keyboards: current-word completion, next-word prediction,
 * correction and a personalized history of words + word pairs/triples.
 *
 * SwiftKey's production model is proprietary; this is an independent
 * implementation of the same public concepts.
 */
class SuggestionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("learned_words_v2", Context.MODE_PRIVATE)

    private val arWords = listOf(
        "أنا","أنت","أنتي","نحن","هو","هي","هم","هذا","هذه","هنا","هناك","من","ما","ماذا","متى","أين","كيف","لماذا",
        "الذي","التي","الذين","كل","بعض","أي","أيضا","أيضًا","فقط","جدا","جدًا","تقريبا","تقريبًا","ربما","أكيد","بالتأكيد",
        "مرحبا","مرحباً","السلام","عليكم","وعليكم","شكرا","شكرًا","شكراً","العفو","آسف","عفوا","أهلا","أهلاً","وسهلاً",
        "أريد","اريد","أحتاج","احتاج","أستطيع","استطيع","يمكن","ممكن","لازم","يجب","أحب","احب","أعرف","اعرف","أفهم","افهم",
        "اليوم","غدا","غدًا","أمس","الآن","الان","بعد","قبل","دائما","دائمًا","أحيانا","أحيانًا","بكرة","غداً",
        "البيت","العمل","السوق","المدرسة","الجامعة","المستشفى","الشارع","المطعم","المكتب","السيارة","الهاتف",
        "التطبيق","الكيبورد","لوحة","المفاتيح","كلمة","كلمات","نص","رسالة","رسائل","برنامج","مشروع","رابط","ملف",
        "صورة","فيديو","مشكلة","حل","طريقة","خطوة","إعدادات","حجم","شريط","اقتراحات","توقع","تنبؤ","كلام","شيء","شي",
        "جديد","جديدة","قديم","جميل","جميلة","تمام","جيد","جيدة","ممتاز","صحيح","خطأ","نعم","لا","لكن","لأن","لذلك","إذا","ثم",
        "مع","بدون","على","في","منذ","حتى","عن","و","أو","وقت","ساعة","دقيقة","يوم","أسبوع","شهر","سنة",
        "تعال","تعالي","اذهب","روح","شوف","انظر","قل","أرسل","ارسل","افتح","اغلق","أغلق","اكتب","اقرأ","ساعدني","ساعد",
        "تستطيع","تقدر","أخبرني","اخبرني","أرسل","أعطني","اعطني","من","فضلك","سمحت","الله","خير","مساعدة","المساعدة",
        "مشروع","تطبيق","هاتف","جهاز","رسالة","رسائل","صديقي","صديقتي","حبيبي","حبيبتي","جميل","رائع","ممتاز","صحيح",
        "ممكن","أريد","أحتاج","أستطيع","سوف","سأكون","سأذهب","سوف أرسل","الآن","بعد ذلك","قبل ذلك"
    )

    private val enWords = listOf(
        "i","you","he","she","we","they","it","this","that","these","those","there","here","the","a","an","and","or","but",
        "if","then","so","because","for","from","with","without","about","into","on","in","at","to","of","is","are","was","were",
        "have","has","had","will","would","can","could","should","want","need","what","when","where","why","who","which",
        "hello","thanks","thank","please","sorry","welcome","today","tomorrow","yesterday","now","later","before","after",
        "always","sometimes","maybe","really","just","already","still","more","much","many","some","any","all","only","also",
        "very","sure","home","work","school","office","market","restaurant","hospital","street","university","car","phone",
        "app","keyboard","message","messages","text","code","download","android","project","file","link","photo","image",
        "video","settings","problem","solution","way","step","new","good","great","nice","right","wrong","yes","no","okay","ok",
        "morning","afternoon","evening","night","time","hour","minute","day","week","month","year","come","go","look","see",
        "send","open","close","write","read","make","use","help","try","going","first","last","next","best","working","works",
        "please help","help me","can you","could you","what is","what are","where are you","how can i","how do i",
        "let me know","send me","send me the","open the app","open the keyboard","keyboard settings","see you soon",
        "talk to you","have a good","have a great","no problem","that is","this is","there is","there are","i am","i am going",
        "i will","i want to","i need to","i would like to","we can","we need","i think","i know","i do not know",
        "right now","after that","before that","at the moment","in the future","if you want","if you need",
        "you can","you should","please send","please open","please check","good to know","see you tomorrow","have a nice day"
    )

    private val arNext = mapOf(
        "أنا" to listOf("أريد","بخير","في","من","لا","أحب","أحتاج"),
        "انا" to listOf("اريد","بخير","في","من","لا","احب","احتاج"),
        "أنت" to listOf("بخير","تستطيع","يمكنك","الآن","ماذا","تريد"),
        "أريد" to listOf("أن","ال","من","شيء","هذا","مساعدة","أذهب","أعرف"),
        "اريد" to listOf("ان","ال","من","شي","هذا","مساعدة","اذهب","اعرف"),
        "أحتاج" to listOf("إلى","مساعدة","من","هذا","شيء","أن"),
        "احتاج" to listOf("الى","مساعدة","من","هذا","شي","ان"),
        "كيف" to listOf("يمكنني","أستطيع","حالك","تعمل","ذلك","أفعل","يمكن"),
        "ماذا" to listOf("تفعل","تريد","هذا","هي","هو","الآن","تقول"),
        "هل" to listOf("يمكن","تستطيع","تقدر","هذا","هناك","ممكن","أنت"),
        "من" to listOf("فضلك","أجل","الممكن","البيت","العمل","أجل"),
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
        "على" to listOf("كل","الأقل","هذا"),
        "هذا" to listOf("هو","شيء","الذي","المشروع"),
        "هذه" to listOf("هي","الكلمة","المشكلة","الطريقة"),
        "كيف يمكن" to listOf("أن","ني","ذلك"),
        "أرسل" to listOf("لي","هذا","الرسالة","الرابط"),
        "افتح" to listOf("التطبيق","الكيبورد","الرابط","الإعدادات"),
        "أكتب" to listOf("الكلمة","الرسالة","هذا","لك"),
        "ساعدني" to listOf("في","من","على","أريد")
    )

    private val enNext = mapOf(
        "i" to listOf("am","want","need","will","can","think","know","have"),
        "you" to listOf("are","can","will","have","want","need","should","know"),
        "we" to listOf("are","can","will","need","have","should","want"),
        "they" to listOf("are","will","can","have","want"),
        "he" to listOf("is","will","can","has","was"),
        "she" to listOf("is","will","can","has","was"),
        "the" to listOf("best","next","first","same","way","keyboard","app","new"),
        "a" to listOf("new","good","great","little","lot","way","message"),
        "an" to listOf("example","app","idea","important"),
        "what" to listOf("is","are","do","do you","about","happened"),
        "how" to listOf("are","can","do","do i","to","much"),
        "where" to listOf("are","is","can","do","you"),
        "when" to listOf("you","will","can","is","are"),
        "can" to listOf("you","i","we","be","help","do"),
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
        "no" to listOf("problem","thanks","one","more"),
        "how do" to listOf("i","you","we"),
        "let me" to listOf("know","see","check"),
        "see you" to listOf("soon","tomorrow","later"),
        "have a" to listOf("good","great","nice")
    )

    private data class Scored(val word: String, val score: Int)

    fun suggestions(textBeforeCursor: String, arabic: Boolean): List<String> {
        val base = if (arabic) arWords else enWords
        val current = textBeforeCursor.takeLastWhile { !it.isWhitespace() }

        return if (current.isNotEmpty()) {
            currentWordSuggestions(current, base, arabic)
        } else {
            nextWordSuggestions(tokenize(textBeforeCursor), base, arabic)
        }
    }

    fun learnWord(word: String, arabic: Boolean) {
        val clean = word.trim()
        if (!isRealWord(clean) || clean.length > 80) return
        val lang = if (arabic) "ar" else "en"
        val normalized = normalize(clean, arabic)
        val key = "$lang:word:$normalized"
        val old = prefs.getInt(key, 0)
        prefs.edit().putInt(key, (old + 1).coerceAtMost(5000)).apply()
    }

    fun learnContext(textBeforeCursor: String, arabic: Boolean) {
        val tokens = tokenize(textBeforeCursor).takeLast(20)
        if (tokens.isEmpty()) return

        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { normalize(it, arabic) }
        val editor = prefs.edit()

        normalized.forEach { token ->
            if (token.isNotEmpty()) {
                val key = "$lang:word:$token"
                editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(5000))
            }
        }

        for (i in 0 until normalized.lastIndex) {
            val a = normalized[i]
            val b = normalized[i + 1]
            if (a.isNotEmpty() && b.isNotEmpty()) {
                val key = "$lang:bi:$a|$b"
                editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(5000))
            }
        }

        for (i in 0 until normalized.size - 2) {
            val a = normalized[i]
            val b = normalized[i + 1]
            val d = normalized[i + 2]
            if (a.isNotEmpty() && b.isNotEmpty() && d.isNotEmpty()) {
                val key = "$lang:tri:$a|$b|$d"
                editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(5000))
            }
        }

        editor.apply()
    }

    private fun currentWordSuggestions(prefixRaw: String, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val prefix = normalize(prefixRaw, arabic)
        val candidates = LinkedHashSet<String>()
        candidates.addAll(base)

        prefs.all.keys
            .filter { it.startsWith("$lang:word:") }
            .forEach { candidates.add(it.removePrefix("$lang:word:")) }

        val exact = ArrayList<Scored>()
        val fuzzy = ArrayList<Scored>()

        for (raw in candidates) {
            val candidate = normalize(raw, arabic)
            if (candidate.isEmpty()) continue
            val freq = prefs.getInt("$lang:word:$candidate", 0)

            if (candidate.startsWith(prefix)) {
                val completionBonus = if (candidate == prefix) 500 else 3000
                val lengthBonus = max(0, 180 - (candidate.length - prefix.length) * 12)
                exact += Scored(display(candidate, base, arabic), completionBonus + lengthBonus + freq * 180)
            } else if (prefix.length >= 2) {
                val probe = candidate.take(prefix.length.coerceAtLeast(1))
                val distance = levenshtein(prefix, probe)
                if (distance <= 2) {
                    fuzzy += Scored(display(candidate, base, arabic), 1500 + freq * 120 - distance * 350 - candidate.length)
                }
            }
        }

        // Always show the literal text so a new personal word can be taught.
        exact += Scored(prefixRaw, 4200)

        return (exact.sortedByDescending { it.score } + fuzzy.sortedByDescending { it.score })
            .map { it.word }
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it, arabic) }
            .take(3)
    }

    private fun nextWordSuggestions(tokens: List<String>, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { normalize(it, arabic) }.filter { it.isNotEmpty() }
        val last = normalized.lastOrNull()
        val previous = normalized.getOrNull(normalized.lastIndex - 1)
        val score = LinkedHashMap<String, Int>()

        fun add(raw: String, points: Int) {
            val word = raw.trim()
            if (!isRealWord(word)) return
            val key = normalize(word, arabic)
            score[key] = (score[key] ?: 0) + points
        }

        // Personal 3-word history has the strongest contextual weight.
        if (previous != null && last != null) {
            val prefix = "$lang:tri:$previous|$last|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 5000 + prefs.getInt(key, 0) * 500)
            }
        }

        // Then personal 2-word history.
        if (last != null) {
            val prefix = "$lang:bi:$last|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 3500 + prefs.getInt(key, 0) * 350)
            }

            // Built-in language knowledge gives useful predictions before the
            // keyboard has learned enough from this user.
            val map = if (arabic) arNext else enNext
            map[last]?.forEachIndexed { index, word ->
                add(word, 2600 - index * 120)
            }

            // Also support the last two words as a phrase key.
            if (previous != null) {
                map["$previous $last"]?.forEachIndexed { index, word ->
                    add(word, 3200 - index * 120)
                }
            }
        }

        // Learned word frequency is a weaker fallback signal.
        prefs.all.keys.filter { it.startsWith("$lang:word:") }.forEach { key ->
            val word = key.removePrefix("$lang:word:")
            add(word, 200 + prefs.getInt(key, 0) * 35)
        }

        // Fresh install fallback: always return useful language words.
        base.forEachIndexed { index, word ->
            add(word, 120 - index.coerceAtMost(80))
        }

        return score.entries
            .sortedByDescending { it.value }
            .map { display(it.key, base, arabic) }
            .distinctBy { normalize(it, arabic) }
            .take(3)
    }

    private fun tokenize(text: String): List<String> {
        // Correct Unicode-letter regex. The previous version over-escaped
        // this expression, which prevented reliable context learning.
        val regex = Regex("[\\p{L}\\p{M}\\p{Nd}']+")
        val result = regex.findAll(text).map { it.value }.toList()
        return if (result.isNotEmpty()) result else {
            text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
    }

    private fun display(word: String, base: List<String>, arabic: Boolean): String =
        base.firstOrNull { normalize(it, arabic) == normalize(word, arabic) } ?: word

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

    private fun isRealWord(word: String): Boolean =
        word.isNotBlank() && word.any { it.isLetter() } && !word.any { it.isWhitespace() }

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
