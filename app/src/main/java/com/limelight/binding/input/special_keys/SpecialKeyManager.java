package com.limelight.binding.input.special_keys;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpecialKeyManager {

    private static final String PREFS_NAME = "CustomSpecialKeys";
    private static final String KEY_COUNT_PREFIX = "customKey_count";
    private static final String KEY_ITEM_PREFIX = "customKey_item_";
    private static final String DEFAULT_KEYS = "default_keys";

    private final SharedPreferences sharedPreferences;
    private final Context context;

    public SpecialKeyManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Predefined();
    }

    public static class CustomKey {
        public String label;
        public short[] keys;

        public CustomKey(String label, short[] keys) {
            this.label = label;
            this.keys = keys;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomKey customKey = (CustomKey) o;
            return label.equals(customKey.label) && Arrays.equals(keys, customKey.keys);
        }

        @Override
        public int hashCode() {
            int result = label.hashCode();
            result = 31 * result + Arrays.hashCode(keys);
            return result;
        }
    }

    // supply default keys for user to use
    private void Predefined() {
        boolean predefinedKeys = sharedPreferences.getBoolean(DEFAULT_KEYS, false);

        if (!predefinedKeys) {
            List<CustomKey> defaultKeys = new ArrayList<>();
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_esc),
                    new short[]{KeyboardTranslator.VK_ESCAPE}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_f11),
                    new short[]{KeyboardTranslator.VK_F11}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_f11_alt),
                    new short[]{KeyboardTranslator.VK_MENU, KeyboardTranslator.VK_RETURN}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_win),
                    new short[]{KeyboardTranslator.VK_LWIN}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_win_d),
                    new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_win_g),
                    new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_keys_shift_tab),
                    new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB}));
            defaultKeys.add(new CustomKey(context.getString(R.string.game_menu_send_move_window),
                    new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_RIGHT}));

            saveCustomKeys(defaultKeys);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(DEFAULT_KEYS, true);
            editor.apply();
        }
    }

    public List<CustomKey> getCustomKeys() {
        List<CustomKey> customKeys = new ArrayList<>();
        int keyCount = sharedPreferences.getInt(KEY_COUNT_PREFIX, 0);

        for (int i = 0; i < keyCount; i++) {
            String label = sharedPreferences.getString(KEY_ITEM_PREFIX + i + "_label", null);
            String keysString = sharedPreferences.getString(KEY_ITEM_PREFIX + i + "_keys", null);

            if (label != null && keysString != null) {
                customKeys.add(new CustomKey(label, stringToShortArray(keysString)));
            }
        }
        return customKeys;
    }

    public void saveCustomKeys(List<CustomKey> customKeys) {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        int oldKeyCount = sharedPreferences.getInt(KEY_COUNT_PREFIX, 0);
        for (int i = 0; i < oldKeyCount; i++) {
            editor.remove(KEY_ITEM_PREFIX + i + "_label");
            editor.remove(KEY_ITEM_PREFIX + i + "_keys");
        }

        for (int i = 0; i < customKeys.size(); i++) {
            CustomKey key = customKeys.get(i);
            editor.putString(KEY_ITEM_PREFIX + i + "_label", key.label);
            editor.putString(KEY_ITEM_PREFIX + i + "_keys", shortArrayToString(key.keys));
        }
        editor.putInt(KEY_COUNT_PREFIX, customKeys.size());
        editor.apply();
    }

    public void addCustomKey(CustomKey customKey) {
        List<CustomKey> customKeys = getCustomKeys();
        customKeys.add(customKey);
        saveCustomKeys(customKeys);
    }

    public void removeCustomKey(CustomKey customKey) {
        List<CustomKey> customKeys = getCustomKeys();
        customKeys.remove(customKey);
        saveCustomKeys(customKeys);
    }

    public void resetToDefaults() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        int oldKeyCount = sharedPreferences.getInt(KEY_COUNT_PREFIX, 0);
        for (int i = 0; i < oldKeyCount; i++) {
            editor.remove(KEY_ITEM_PREFIX + i + "_label");
            editor.remove(KEY_ITEM_PREFIX + i + "_keys");
        }
        editor.remove(KEY_COUNT_PREFIX);

        editor.putBoolean(DEFAULT_KEYS, false);
        editor.apply();

        Predefined();
    }

    private String shortArrayToString(short[] array) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private short[] stringToShortArray(String str) {
        if (str == null || str.isEmpty()) {
            return new short[0];
        }
        String[] parts = str.split(",");
        short[] array = new short[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                array[i] = Short.parseShort(parts[i].trim());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                array[i] = 0;
            }
        }
        return array;
    }
}