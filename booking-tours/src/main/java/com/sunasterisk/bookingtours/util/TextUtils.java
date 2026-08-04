package com.sunasterisk.bookingtours.util;

import java.text.Normalizer;

/** Tiện ích xử lý text, đặc biệt cho tiếng Việt. */
public final class TextUtils {

    private TextUtils() {}

    /**
     * Chuẩn hóa chuỗi để tra cứu không phân biệt dấu thanh.
     *
     * <p>Áp dụng NFD decomposition rồi xóa toàn bộ combining diacritical marks,
     * giải quyết vấn đề "hóa" (tone trên o) vs "hoá" (tone trên a) trong tiếng Việt.</p>
     *
     * <p>Ví dụ: "Du lịch văn hóa" và "Du lịch văn hoá" đều → "du lich van hoa"</p>
     */
    public static String normalizeForLookup(String s) {
        if (s == null) return "";
        String decomposed = Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }
}
