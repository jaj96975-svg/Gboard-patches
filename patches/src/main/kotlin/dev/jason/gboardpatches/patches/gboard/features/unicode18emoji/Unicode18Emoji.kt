package dev.jason.gboardpatches.patches.gboard.features.unicode18emoji

import app.morphe.patcher.patch.bytecodePatch
import java.io.InputStream

private const val DATA_PATH = "/unicode18emoji/unicode18.json"

data class Unicode18Emoji(
    val codepoints: String,
    val emoji: String,
    val name: String,
    val group: String,
    val subgroup: String
)

private fun loadUnicode18Catalog(): String {
    val stream: InputStream =
        Unicode18EmojiPatch::class.java.getResourceAsStream(DATA_PATH)
            ?: error("Missing resource: $DATA_PATH")

    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/*
 * Unicode 18.0 emoji patch for Gboard 17.7.7.
 *
 * This patch intentionally does NOT use Gboard feature flags.
 *
 * The actual Gboard emoji-catalog bytecode hook must be matched against
 * the target 17.7.7 APK because Gboard's internal classes are obfuscated
 * and can change between builds.
 */
val Unicode18EmojiPatch = bytecodePatch(
    name = "Add Unicode 18.0 Emojis",
    description = "Adds Unicode 18.0 emoji catalog data to Gboard"
) {
    execute {
        val catalogJson = loadUnicode18Catalog()

        /*
         * TODO: Target Gboard 17.7.7's emoji catalog.
         *
         * Required operations:
         *
         * 1. Locate the emoji catalog/data loader.
         * 2. Parse the existing catalog.
         * 3. Load unicode18.json.
         * 4. Append the Unicode 18 entries.
         * 5. Preserve Gboard's existing emoji categories/order.
         * 6. Return the modified catalog to the emoji picker.
         *
         * Do NOT replace this with a guessed class or method name.
         * Gboard's implementation is obfuscated and version-specific.
         */

        check(catalogJson.isNotBlank()) {
            "Unicode 18 emoji catalog is empty"
        }
    }
}
