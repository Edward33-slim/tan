package com.keyboards10

import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * Local contextual prediction engine.
 *
 * The interaction model follows the same basic pattern used by SwiftKey:
 * - while a word is being typed: complete/correct the current word
 * - after a space: predict the next word from sentence context
 * - learn words and 2/3-word combinations locally on the device
 *
 * This is an independent local model; it does not use or copy SwiftKey's
 * proprietary language model.
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
        "و","أو","أيضا","أيضًا","فقط","جدا","جدًا","تقريبًا","ربما","أكيد","بالتأكيد",
        "صباح","مساء","ليل","وقت","ساعة","دقيقة","يوم","أسبوع","شهر","سنة","بكرة","الآن",
        "تعال","تعالي","اذهب","روح","شوف","انظر","قل","أرسل","ارسل","افتح","اغلق","اكتب","اقرأ",
        "سوف","راح","سأ","يكون","تكون","كان","كانت","صار","صارت","عندي","عندك","لدينا",
        "ساعدني","تساعدني","تستطيع","تقدر","أخبرني","اخبرني","أرسل لي","ارسل لي","أعطني","اعطني",
        "أريد أن","اريد ان","أحتاج إلى","احتاج الى","من فضلك","لو سمحت","إن شاء الله","ان شاء الله",
        "الله","الله يبارك","شكرا لك","شكراً لك","بارك الله فيك","كل شيء","كل شي","لا مشكلة","لا بأس"
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
        "going","just","already","still","first","last","next","please","okay","ok","right","wrong",
        "there","here","more","much","many","some","any","all","one","two","first","best","new",
        "work","working","works","need","needed","want","wanted","thanks","welcome","sorry"
    )

    // Common sentence fragments give the offline model useful cold-start
    // context before the user has taught it their own writing style.
    private val arPhrases = listOf(
        "السلام عليكم","وعليكم السلام","صباح الخير","مساء الخير","ليلة سعيدة",
        "كيف حالك","كيف حالكم","أنا بخير","انا بخير","الحمد لله",
        "شكرا لك","شكراً لك","العفو","الله يبارك فيك","بارك الله فيك",
        "أريد أن","اريد ان","أحتاج إلى","احتاج الى","ممكن تساعدني","هل يمكنك",
        "هل ممكن","هل تستطيع","هل تقدر","ما هو","ما هي","أين أنت","اين انت",
        "ماذا تريد","ماذا تفعل","كيف يمكنني","كيف أستطيع","أخبرني عن","اخبرني عن",
        "من فضلك","لو سمحت","لا مشكلة","لا بأس","كل شيء","كل شي",
        "أنا في البيت","انا في البيت","أنا في العمل","انا في العمل",
        "أرسل لي","ارسل لي","أرسل الملف","ارسل الملف","أرسل الصورة","ارسل الصورة",
        "افتح التطبيق","افتح الكيبورد","لوحة المفاتيح","إعدادات الكيبورد",
        "اليوم إن شاء الله","اليوم ان شاء الله","غدا إن شاء الله","غدا ان شاء الله",
        "سوف يكون","راح يكون","أريد أن أكتب","اريد ان اكتب","ممكن ترسل لي",
        "ممكن ترسل","أريد منك","اريد منك","أحتاج منك","احتاج منك",
        "أنا أريد","انا اريد","أنا أحب","انا احب","أنا لا أعرف","انا لا اعرف",
        "لا أعرف","لا اعرف","لا أستطيع","لا استطيع","يمكنك أن","تقدر أن",
        "إذا أردت","اذا اردت","إذا كان","اذا كان","بعد ذلك","قبل ذلك",
        "في الوقت الحالي","في نفس الوقت","في هذا الوقت","على كل حال",
        "شكرا جزيلا","شكراً جزيلاً","مرحبا بك","مرحبا بكم","أهلا وسهلا","اهلا وسهلا"
    )

    private val enPhrases = listOf(
        "hello there","good morning","good evening","good night","how are you","I am fine",
        "thank you","thank you very much","thanks for","you are welcome","please help me",
        "can you help","could you please","what is the","what are the","where are you",
        "what do you","what are you","how can I","how do I","let me know","send me the",
        "send me","open the app","open the keyboard","keyboard settings","see you soon",
        "talk to you","have a good","have a great","no problem","that is","this is",
        "there is","there are","I am going","I will be","I want to","I need to",
        "I would like to","we can","we need","I think","I know","I do not know",
        "right now","for the next","after that","before that","at the moment",
        "in the future","in this case","if you want","if you need","you can",
        "you should","please send","please open","please check","good to know",
        "see you tomorrow","have a nice day","have a good day"
    )

    private data class Scored(val word: String, val score: Int)

    fun suggestions(textBeforeCursor: String, arabic: Boolean): List<String> {
        val current = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
        val tokens = tokenize(textBeforeCursor)
        val base = if (arabic) ar else en

        return if (current.isNotEmpty()) {
            currentWordCompletions(current, base, arabic)
        } else {
            nextWordPredictions(tokens, base, arabic)
        }
    }

    fun learn(word: String, arabic: Boolean) {
        val clean = word.trim()
        if (clean.isEmpty() || clean.length > 100 || clean.any { it.isWhitespace() }) return
        val lang = if (arabic) "ar" else "en"
        val normalized = clean.lowercase(Locale.ROOT)
        val key = lang + ":word:" + normalized
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun learnContext(textBeforeCursor: String, arabic: Boolean) {
        val tokens = tokenize(textBeforeCursor).takeLast(8)
        if (tokens.isEmpty()) return

        val lang = if (arabic) "ar" else "en"
        val editor = prefs.edit()

        // Learn every recent word, not only the last three.
        tokens.forEach { token ->
            val key = lang + ":word:" + token.lowercase(Locale.ROOT)
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        // Learn all recent adjacent pairs.
        for (i in 0 until tokens.lastIndex) {
            val a = tokens[i].lowercase(Locale.ROOT)
            val b = tokens[i + 1].lowercase(Locale.ROOT)
            val key = lang + ":bi:" + a + "|" + b
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        // Learn all recent triples.
        for (i in 0 until tokens.size - 2) {
            val a = tokens[i].lowercase(Locale.ROOT)
            val b = tokens[i + 1].lowercase(Locale.ROOT)
            val c = tokens[i + 2].lowercase(Locale.ROOT)
            val key = lang + ":tri:" + a + "|" + b + "|" + c
            editor.putInt(key, prefs.getInt(key, 0) + 1)
        }

        editor.apply()
    }

    private fun currentWordCompletions(
        prefixRaw: String,
        base: List<String>,
        arabic: Boolean
    ): List<String> {
        val lang = if (arabic) "ar" else "en"
        val prefix = if (arabic) prefixRaw else prefixRaw.lowercase(Locale.ROOT)
        val all = LinkedHashSet<String>()
        all.addAll(base)

        prefs.all.keys
            .filter { it.startsWith(lang + ":word:") }
            .forEach { key -> all.add(key.removePrefix(lang + ":word:")) }

        val exact = ArrayList<Scored>()
        val fuzzy = ArrayList<Scored>()

        for (candidateRaw in all) {
            val candidate = if (arabic) candidateRaw else candidateRaw.lowercase(Locale.ROOT)
            val count = prefs.getInt(lang + ":word:" + candidate.lowercase(Locale.ROOT), 0)

            if (candidate.startsWith(prefix, ignoreCase = !arabic)) {
                val exactBonus = if (candidate.equals(prefix, ignoreCase = !arabic)) 9_000 else 2_000
                val shortBonus = max(0, 80 - candidate.length * 2)
                exact += Scored(candidateRaw, exactBonus + count * 45 + shortBonus)
            } else if (prefix.length >= 2) {
                val probe = candidate.take(max(prefix.length, 1))
                val distance = levenshtein(prefix, probe)
                if (distance <= 1) {
                    fuzzy += Scored(candidateRaw, 900 + count * 35 - distance * 160 - candidate.length)
                }
            }
        }

        // If the user typed a new word that the model has never seen, keep
        // the literal typed word available as one of the three candidates.
        val literal = prefixRaw.trim()
        if (literal.isNotEmpty()) {
            exact += Scored(literal, 8_500)
        }

        return (exact.sortedByDescending { it.score } + fuzzy.sortedByDescending { it.score })
            .map { it.word }
            .distinctBy { if (arabic) it else it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun nextWordPredictions(
        tokens: List<String>,
        base: List<String>,
        arabic: Boolean
    ): List<String> {
        val lang = if (arabic) "ar" else "en"
        val normalized = tokens.map { it.lowercase(Locale.ROOT) }
        val last = normalized.lastOrNull()
        val previous = normalized.getOrNull(normalized.lastIndex - 1)
        val phrases = if (arabic) arPhrases else enPhrases
        val score = LinkedHashMap<String, Int>()

        fun add(word: String, amount: Int) {
            if (word.isBlank()) return
            val clean = word.trim()
            score[clean.lowercase(Locale.ROOT)] =
                (score[clean.lowercase(Locale.ROOT)] ?: 0) + amount
        }

        // Phrase corpus: both bigram and trigram context are useful at cold start.
        if (last != null) {
            phrases.forEach { phrase ->
                val parts = tokenize(phrase)
                for (i in 0 until parts.lastIndex) {
                    if (parts[i].equals(last, ignoreCase = true)) {
                        add(parts[i + 1], 220)
                    }
                }
                if (previous != null) {
                    for (i in 0 until parts.size - 2) {
                        if (parts[i].equals(previous, true) &&
                            parts[i + 1].equals(last, true)
                        ) {
                            add(parts[i + 2], 420)
                        }
                    }
                }
            }
        }

        // Learned personal language model gets higher weight than the
        // cold-start corpus.
        if (previous != null && last != null) {
            val prefix = lang + ":tri:" + previous + "|" + last + "|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                val next = key.substringAfterLast('|')
                add(next, 900 + prefs.getInt(key, 0) * 80)
            }
        }

        if (last != null) {
            val prefix = lang + ":bi:" + last + "|"
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { key ->
                val next = key.substringAfterLast('|')
                add(next, 600 + prefs.getInt(key, 0) * 55)
            }
        }

        // Personal word frequency is a secondary signal.
        prefs.all.keys
            .filter { it.startsWith(lang + ":word:") }
            .forEach { key ->
                val word = key.removePrefix(lang + ":word:")
                add(word, prefs.getInt(key, 0) * 8)
            }

        // Cold-start vocabulary. Lower than contextual matches so that
        // context wins whenever there is evidence for it.
        base.forEachIndexed { index, word ->
            add(word, max(1, 70 - index))
        }

        return score.entries
            .sortedByDescending { it.value }
            .map { restoreCase(it.key, base, arabic) }
            .distinctBy { if (arabic) it else it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun restoreCase(word: String, base: List<String>, arabic: Boolean): String {
        return base.firstOrNull { it.equals(word, ignoreCase = true) } ?: word
    }

    private fun tokenize(text: String): List<String> =
        Regex("[\\p{L}\\p{M}\\p{Nd}']+")
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
