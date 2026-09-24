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

        // SwiftKey-style behavior:
        // - while a word is being typed: left/right completions + the
        //   user's literal word in the center
        // - after a completed word: three next-word predictions
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
        val tokens = tokenize(textBeforeCursor)
            .map { normalize(it, arabic) }
            .filter { it.isNotEmpty() }
            .takeLast(8)

        if (tokens.isEmpty()) return

        val lang = if (arabic) "ar" else "en"
        val editor = prefs.edit()

        // Only reinforce the newly completed word and its immediate context.
        // This prevents older words from being artificially counted again
        // every time the user presses space.
        val last = tokens.last()
        val wordKey = "$lang:word:$last"
        editor.putInt(wordKey, (prefs.getInt(wordKey, 0) + 1).coerceAtMost(5000))

        if (tokens.size >= 2) {
            val a = tokens[tokens.lastIndex - 1]
            val key = "$lang:bi:$a|$last"
            editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(5000))
        }

        if (tokens.size >= 3) {
            val a = tokens[tokens.lastIndex - 2]
            val b = tokens[tokens.lastIndex - 1]
            val key = "$lang:tri:$a|$b|$last"
            editor.putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(5000))
        }

        editor.apply()
    }

    private fun currentWordSuggestions(prefixRaw: String, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val prefix = normalize(prefixRaw, arabic)
        if (prefix.isEmpty()) return nextWordSuggestions(emptyList(), base, arabic)

        val candidates = LinkedHashSet<String>()
        base.filter { isSingleToken(it) }.forEach { candidates.add(it) }

        prefs.all.keys
            .filter { it.startsWith("$lang:word:") }
            .forEach {
                val word = it.removePrefix("$lang:word:")
                if (isSingleToken(word)) candidates.add(word)
            }

        val scored = ArrayList<Scored>()

        for (raw in candidates) {
            val candidate = normalize(raw, arabic)
            if (candidate.isEmpty() || candidate == prefix) continue

            val freq = prefs.getInt("$lang:word:$candidate", 0)

            if (candidate.startsWith(prefix)) {
                val completionLength = candidate.length - prefix.length
                val score = 10000 +
                    freq * 260 +
                    max(0, 1400 - completionLength * 90) +
                    if (candidate.length == prefix.length + 1) 500 else 0
                scored += Scored(display(candidate, base, arabic), score)
            } else if (prefix.length >= 2) {
                val probe = candidate.take(prefix.length.coerceAtMost(candidate.length))
                val distance = levenshtein(prefix, probe)
                if (distance <= 2) {
                    scored += Scored(
                        display(candidate, base, arabic),
                        4200 + freq * 180 - distance * 850 - candidate.length * 8
                    )
                }
            }
        }

        val alternatives = scored
            .sortedByDescending { it.score }
            .map { it.word }
            .filter { normalize(it, arabic) != prefix }
            .distinctBy { normalize(it, arabic) }
            .take(8)

        // SwiftKey's familiar three-slot arrangement keeps the user's
        // current text in the middle and places corrections/completions
        // around it.
        val result = ArrayList<String>(3)
        if (alternatives.isNotEmpty()) result += alternatives[0]
        result += prefixRaw
        if (alternatives.size > 1) result += alternatives[1]

        // If the dictionary has no useful match, keep the typed word visible
        // and fill the remaining slots with language fallbacks.
        if (result.size < 3) {
            for (word in base) {
                if (result.size >= 3) break
                if (isSingleToken(word) &&
                    result.none { normalize(it, arabic) == normalize(word, arabic) }
                ) result += word
            }
        }

        return result.distinctBy { normalize(it, arabic) }.take(3)
    }

    private fun nextWordSuggestions(tokens: List<String>, base: List<String>, arabic: Boolean): List<String> {
        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { normalize(it, arabic) }.filter { it.isNotEmpty() }
        val last = normalized.lastOrNull()
        val previous = normalized.getOrNull(normalized.lastIndex - 1)
        val score = LinkedHashMap<String, Int>()

        fun add(raw: String, points: Int) {
            val word = raw.trim()
            if (!isSingleToken(word)) return
            val key = normalize(word, arabic)
            if (key.isEmpty()) return
            score[key] = (score[key] ?: 0) + points
        }

        // 3-word context is strongest.
        if (previous != null && last != null) {
            val prefix = "$lang:tri:$previous|$last|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 9000 + prefs.getInt(key, 0) * 700)
            }
        }

        // 2-word context is next.
        if (last != null) {
            val prefix = "$lang:bi:$last|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                add(key.substringAfterLast('|'), 6000 + prefs.getInt(key, 0) * 500)
            }

            val map = if (arabic) arNext else enNext

            map[last]?.forEachIndexed { index, word ->
                add(word, 5000 - index * 180)
            }

            if (previous != null) {
                map["$previous $last"]?.forEachIndexed { index, word ->
                    add(word, 7200 - index * 180)
                }
            }
        }

        // Personal vocabulary is a useful fallback, but must not overpower
        // real contextual bigram/trigram predictions.
        prefs.all.keys.filter { it.startsWith("$lang:word:") }.forEach { key ->
            val word = key.removePrefix("$lang:word:")
            add(word, 300 + prefs.getInt(key, 0) * 45)
        }

        // Cold-start language fallback.
        base.forEachIndexed { index, word ->
            add(word, 80 - index.coerceAtMost(60))
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

    private fun isSingleToken(word: String): Boolean =
        word.isNotBlank() &&
            word.any { it.isLetter() } &&
            word.none { it.isWhitespace() || it == '|' }

    private fun isRealWord(word: String): Boolean = isSingleToken(word)

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
