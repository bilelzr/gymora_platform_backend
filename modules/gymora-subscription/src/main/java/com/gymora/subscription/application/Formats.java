package com.gymora.subscription.application;

import com.gymora.security.user.CurrentUser;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/** Amounts, dates and names as they appear in error messages, in the caller's language. */
final class Formats {

    private Formats() {
    }

    /** 60000 millimes → "60,000 DT" in French and Arabic, "60.000 TND" in English. */
    static String amount(long millimes) {
        boolean english = "en".equals(LocaleContextHolder.getLocale().getLanguage());
        NumberFormat format = NumberFormat.getNumberInstance(english ? Locale.US : Locale.FRANCE);
        format.setMinimumFractionDigits(3);
        format.setMaximumFractionDigits(3);
        return format.format(millimes / 1000.0) + (english ? " TND" : " DT");
    }

    static String date(LocalDate day) {
        Locale locale = LocaleContextHolder.getLocale();
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(day);
    }

    /** Name of the signed-in user, as kept on payments, cancellations and freezes. */
    static String currentUserName() {
        return CurrentUser.get()
                .map(user -> user.fullName() != null && !user.fullName().isBlank() ? user.fullName() : user.username())
                .filter(name -> name != null && !name.isBlank())
                .orElse("?");
    }
}
