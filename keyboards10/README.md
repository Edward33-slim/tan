# Keyboards10

Native Android Kotlin keyboard (IME).

- Package: `com.keyboards10`
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36 (Android 16)
- Arabic and English
- Spacebar swipe language switching
- Real host-app text insertion through `InputConnection` (no duplicate editor above the keyboard)
- Contextual local suggestions: current-word completion + next-word prediction + learned words/phrases
- Local clipboard manager with pinning and right-swipe deletion for unpinned items
- Pinned clipboard items are protected from swipe deletion; the pin itself is the only pin toggle target
- Larger clipboard text and pin icon
- Long-press backspace deletes previous words repeatedly while held
- Long Arabic punctuation-key press for diacritics
- Pressed letter/number preview bubble anchored directly above the pressed key
- Enlarged Enter key and wider, low-height spacebar
- Arabic layout places `⌫` beneath `ذ` and `-` in the former backspace slot
- Large emoji panel with categories and larger emoji glyphs
- Android Back from the emoji panel returns to the keyboard without closing the IME
- Settings for keyboard height and key-label size, persisted locally
- No network permission or remote text service

## CodeAssist compatibility
Build tooling was adjusted to Android Gradle Plugin 8.2.2 and Kotlin 1.9.22 to avoid the java.util.stream.Stream.toList() runtime incompatibility on older Android/CodeAssist environments.
