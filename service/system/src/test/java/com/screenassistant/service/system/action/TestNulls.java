package com.screenassistant.service.system.action;

/**
 * Helper de tests: devuelve null con firma de tipo no-null.
 *
 * El mockable android.jar declara getApplicationLabel() como @NonNull CharSequence,
 * por lo que Kotlin/MockK rechazan devolver null desde el stub. Desde Java el null
 * se entrega sin chequeo en runtime, permitiendo ejercitar la defensa del código
 * de producción (pm.getApplicationLabel(info)?.toString()).
 */
public final class TestNulls {
    private TestNulls() {
    }

    public static java.lang.CharSequence nullCharSequence() {
        return null;
    }
}
