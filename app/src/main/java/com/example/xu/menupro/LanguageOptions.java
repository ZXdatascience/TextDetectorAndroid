package com.example.xu.menupro;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by xu on 24/02/18.
 */

@Retention(RetentionPolicy.SOURCE)
@IntDef({LanguageOptions.English, LanguageOptions.French, LanguageOptions.Chinese, LanguageOptions.Japanese})
public @interface LanguageOptions {

    int English = 0;
    int French = 1;
    int Chinese = 2;
    int Japanese = 3;

    class Parser {

        @LanguageOptions
        public static int fromInt(final int value) {
            switch (value) {
                case French:
                    return French;
                case Chinese:
                    return Chinese;
                case Japanese:
                    return Japanese;
                default:
                    return English;
            }
        }
    }

}