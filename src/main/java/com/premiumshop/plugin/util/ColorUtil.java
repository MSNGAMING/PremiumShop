package com.premiumshop.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;

/**
 * Utility class providing color formatting, Adventure Component conversion,
 * MiniMessage parsing, and text effects across Paper/Purpur 1.21.x to 26.3+.
 */
public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    private ColorUtil() {
        // Utility class
    }

    /**
     * Translates alternate color codes using the standard '&amp;' character to a legacy section string.
     *
     * @param text input text
     * @return colorized legacy string
     */
    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        if (text.contains("&")) {
            Component comp = LEGACY_AMPERSAND.deserialize(text);
            return LEGACY_SECTION.serialize(comp);
        }
        return text;
    }

    /**
     * Parses text into an Adventure {@link Component}, supporting both MiniMessage tags and legacy '&amp;' codes.
     *
     * @param text input text
     * @return Adventure Component
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (text.contains("<") && text.contains(">")) {
            try {
                return MINI_MESSAGE.deserialize(text);
            } catch (Exception ignored) {
                // Fall back to legacy if tag syntax fails
            }
        }
        return LEGACY_AMPERSAND.deserialize(text);
    }

    /**
     * Strips all formatting and color codes from text.
     *
     * @param text input text
     * @return stripped text
     */
    public static String stripColor(String text) {
        if (text == null) {
            return "";
        }
        Component comp = parse(text);
        return PlainTextComponentSerializer.plainText().serialize(comp);
    }

    /**
     * Replaces placeholder keys formatted as {key} with their respective values.
     *
     * @param template    base template string
     * @param placeholders mapping of placeholder key to value
     * @return formatted string
     */
    public static String format(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return colorize(result);
    }

    /**
     * Replaces placeholder key-value pairs formatted as {key} with alternating string parameters.
     *
     * @param template base template string
     * @param pairs    alternating key, value pairs
     * @return formatted string
     */
    public static String format(String template, String... pairs) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (int i = 0; i < pairs.length - 1; i += 2) {
            String key = pairs[i];
            String val = pairs[i + 1];
            result = result.replace("{" + key + "}", val != null ? val : "");
        }
        return colorize(result);
    }

    /**
     * Parses a string via Adventure's MiniMessage parser.
     *
     * @param text text to parse
     * @return Adventure Component
     */
    public static Component miniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Creates a two-color linear gradient across the given text using MiniMessage tags.
     *
     * @param text     input text
     * @param startHex start color hex (e.g. "#FFAA00")
     * @param endHex   end color hex (e.g. "#FF5555")
     * @return colorized gradient legacy string
     */
    public static String gradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = startHex.startsWith("#") ? startHex : "#" + startHex;
        String e = endHex.startsWith("#") ? endHex : "#" + endHex;
        Component comp = MINI_MESSAGE.deserialize("<gradient:" + s + ":" + e + ">" + text + "</gradient>");
        return LEGACY_SECTION.serialize(comp);
    }

    /**
     * Builds a visual progress bar string.
     *
     * @param current    current value
     * @param max        maximum value
     * @param length     number of characters in the bar
     * @param filled     character used for completed portion
     * @param empty      character used for uncompleted portion
     * @param filledColor color string/code for completed portion
     * @param emptyColor  color string/code for uncompleted portion
     * @return formatted progress bar
     */
    public static String buildProgressBar(double current, double max, int length,
                                          char filled, char empty,
                                          String filledColor, String emptyColor) {
        if (max <= 0) {
            max = 1;
        }
        double percentage = Math.min(1.0, Math.max(0.0, current / max));
        int completedCount = (int) Math.round(percentage * length);
        int remainingCount = length - completedCount;

        StringBuilder sb = new StringBuilder();
        sb.append(colorize(filledColor));
        for (int i = 0; i < completedCount; i++) {
            sb.append(filled);
        }
        sb.append(colorize(emptyColor));
        for (int i = 0; i < remainingCount; i++) {
            sb.append(empty);
        }
        return sb.toString();
    }
}
