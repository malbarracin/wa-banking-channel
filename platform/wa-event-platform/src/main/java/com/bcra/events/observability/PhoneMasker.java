package com.bcra.events.observability;

public final class PhoneMasker {
    private PhoneMasker() {
    }

    public static String mask(String phone) {
        if (phone == null || !phone.matches("\\+[0-9]{6,15}")) {
            return "***";
        }
        // Keep '+' + first 3 digits + last 2; mask the rest (example: +5491122334455 → +549*********55).
        return phone.substring(0, 4) + "*".repeat(phone.length() - 5) + phone.substring(phone.length() - 2);
    }
}
