package com.sismics.util;

import com.google.common.base.Strings;

import java.util.Locale;

/**
 * Locale utilities.
 *
 * @author jtremeaux
 */
public class LocaleUtil {
    /**
     * Returns a locale from the language / country / variation code (ex: fr_FR).
     * 
     * @param localeCode Locale code
     * @return Locale instance
     */
    public static Locale getLocale(String localeCode) {
        if (Strings.isNullOrEmpty(localeCode)) {
            return Locale.ENGLISH;
        }

        // Map OCR 3-letter / system language codes to Java Locale
        switch (localeCode.toLowerCase()) {
            case "chi_tra":
            case "zh_tw":
            case "zht":
                return new Locale("zh", "TW");
            case "chi_sim":
            case "zh_cn":
            case "zhs":
                return new Locale("zh", "CN");
            case "eng":
            case "en":
                return Locale.ENGLISH;
            case "fra":
            case "fr":
                return Locale.FRENCH;
            case "deu":
            case "de":
                return Locale.GERMAN;
            case "spa":
            case "es":
                return new Locale("es");
            case "ita":
            case "it":
                return Locale.ITALIAN;
            case "por":
            case "pt":
                return new Locale("pt");
            case "rus":
            case "ru":
                return new Locale("ru");
            case "pol":
            case "pl":
                return new Locale("pl");
            case "jpn":
            case "ja":
                return Locale.JAPANESE;
            case "kor":
            case "ko":
                return Locale.KOREAN;
            default:
                break;
        }

        String[] localeCodeArray = localeCode.split("_");
        String language = localeCodeArray[0];
        String country = "";
        String variant = "";
        if (localeCodeArray.length >= 2) {
            country = localeCodeArray[1];
        }
        if (localeCodeArray.length >= 3) {
            variant = localeCodeArray[2];
        }
        return new Locale(language, country, variant);
    }
}
