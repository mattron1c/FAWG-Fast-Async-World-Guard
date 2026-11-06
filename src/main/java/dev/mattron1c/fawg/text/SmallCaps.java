package dev.mattron1c.fawg.text;

/** Converts plain letters to unicode small caps while leaving MiniMessage tags untouched. */
public final class SmallCaps {
    private static final char[] MAP = new char[128];

    static {
        String from = "abcdefghijklmnopqrstuvwxyz";
        String to = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        for (int i = 0; i < from.length(); i++) {
            MAP[from.charAt(i)] = to.charAt(i);
            MAP[Character.toUpperCase(from.charAt(i))] = to.charAt(i);
        }
    }

    private SmallCaps() {}

    /** Converts every letter, ignoring tags (&lt;...&gt;). */
    public static String convert(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { sb.append(c).append(s.charAt(++i)); continue; }
            if (c == '<') depth++;
            if (depth == 0 && c < 128 && MAP[c] != 0) sb.append(MAP[c]); else sb.append(c);
            if (c == '>' && depth > 0) depth--;
        }
        return sb.toString();
    }

    /** Converts only content wrapped in &lt;sc&gt;...&lt;/sc&gt; and strips those tags. */
    public static String apply(String s, boolean enabled) {
        if (s.indexOf("<sc>") < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (true) {
            int a = s.indexOf("<sc>", i);
            if (a < 0) { out.append(s, i, s.length()); break; }
            out.append(s, i, a);
            int b = s.indexOf("</sc>", a);
            if (b < 0) b = s.length();
            String inner = s.substring(a + 4, b);
            out.append(enabled ? convert(inner) : inner);
            i = Math.min(s.length(), b + 5);
        }
        return out.toString();
    }
}
