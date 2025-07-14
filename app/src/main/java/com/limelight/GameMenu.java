
package com.limelight;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GameMenu {

    private static final String PREFS_NAME = "game_menu_prefs";
    private static final String PREFS_CUSTOM_KEYS = "custom_send_keys";
    private static final String PREFS_FIRST_RUN = "first_run_keys";
    private static final long TEST_GAME_FOCUS_DELAY = 10;
    private static final long KEY_UP_DELAY = 25;
    private final List<MenuOption> customSendKeyOptions = new ArrayList<>();

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;
        private final String keysString; // nullable for default keys

        public MenuOption(String label, boolean withGameFocus, String keysString, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.keysString = keysString;
            this.runnable = runnable;
        }

        public MenuOption(String label, String keysString, Runnable runnable) {
            this(label, false, keysString, runnable);
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, null, runnable);
        }

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this(label, withGameFocus, null, runnable);
        }
    }

    private final Game game;
    private final NvConnection conn;
    private final GameInputDevice device;

    public GameMenu(Game game, NvConnection conn, GameInputDevice device) {
        this.game = game;
        this.conn = conn;
        this.device = device;

        loadCustomSendKeys();
        showMenu();
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    private short[] parseKeyNamesToShorts(String input) {
        String[] keyNames = input.toUpperCase().split(",");
        List<Short> keys = new ArrayList<>();
        KeyboardTranslator translator = new KeyboardTranslator();

        for (String name : keyNames) {
            name = name.trim();
            int keycode = KeyboardTranslator.getKeyEventCodeFromName(name);

            if (keycode != KeyEvent.KEYCODE_UNKNOWN) {
                keys.add(translator.translate(keycode, -1));
            } else {
                // Show toast, and signal failure by returning null
                Toast.makeText(game, "Unsupported key: " + name, Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        short[] result = new short[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            result[i] = keys.get(i);
        }
        return result;
    }

    private static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN:
                return KeyboardPacket.MODIFIER_META;

            default:
                return 0;
        }
    }

    private void sendKeys(short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showSidebarMenu(String title, MenuOption[] options, boolean isSubMenu) {
        game.runOnUiThread(() -> {
            View sidebar = game.findViewById(R.id.game_menu_sidebar);

            TextView disconnect = sidebar.findViewById(R.id.disconnect_text);
            disconnect.setOnClickListener(v -> game.disconnect());

            ListView listView = sidebar.findViewById(R.id.menu_list);
            List<String> menuLabels = new ArrayList<>();
            if (isSubMenu) {
                menuLabels.add("← Back");
            }
            for (MenuOption option : options) {
                menuLabels.add(option.label);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(game, R.layout.game_menu_item, R.id.menu_item_text, menuLabels);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                if (isSubMenu && position == 0) {
                    showMenu();
                    return;
                }
                int realPosition = isSubMenu ? position - 1 : position;
                run(options[realPosition]);
            });

            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                if (isSubMenu) {
                    int realPosition = position - 1;
                    if (realPosition >= 0 && realPosition < options.length) {
                        MenuOption option = options[realPosition];

                        // Only show edit dialog if this is not "Add", "Clear", or "Restore"
                        if (!option.label.equals("➕ Add Custom Key Action") &&
                                !option.label.equals("🗑️ Clear All Custom Keys") &&
                                !option.label.equals("♻️ Restore Default Keys")) {

                            showCustomKeyEditDialog(realPosition, option.label);
                        }
                    }
                }
                return true;
            });

            sidebar.setVisibility(View.VISIBLE);
        });
    }


    private void showCustomKeyEditDialog(int index, String label) {
        new AlertDialog.Builder(game)
                .setTitle("Edit or Remove")
                .setMessage("Modify or delete \"" + label + "\"?")
                .setPositiveButton("Edit", (dialog, which) -> showEditDialog(index, label))
                .setNeutralButton("Delete", (dialog, which) -> {
                    customSendKeyOptions.remove(index);
                    saveCustomSendKeys();
                    showSpecialKeysMenu(); // Refresh menu
                    Toast.makeText(game, "Removed.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void addCustomSendKey(String label, String keysString, short... keys) {
        customSendKeyOptions.add(new MenuOption(label, keysString, () -> sendKeys(keys)));
        saveCustomSendKeys();
    }

    private void saveCustomSendKeys() {
        SharedPreferences prefs = game.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();

        for (MenuOption option : customSendKeyOptions) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("label", option.label);
                obj.put("keys", option.keysString); // Save the actual keys string
                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefs.edit().putString(PREFS_CUSTOM_KEYS, jsonArray.toString()).apply();
    }

    private void loadCustomSendKeys() {
        SharedPreferences prefs = game.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(PREFS_CUSTOM_KEYS, null);
        boolean firstRun = prefs.getBoolean(PREFS_FIRST_RUN, true);

        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String label = obj.getString("label");
                    String keys = obj.getString("keys");
                    short[] keyCodes = parseKeyNamesToShorts(keys);
                    customSendKeyOptions.add(new MenuOption(label, keys, () -> sendKeys(keyCodes)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (firstRun) {
            addDefaultKeys();
            prefs.edit().putBoolean(PREFS_FIRST_RUN, false).apply();
            saveCustomSendKeys();
        }
    }

    private void addDefaultKeys() {
        customSendKeyOptions.clear();

        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                "ESC", () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                "F11", () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_f11_alt),
                "ALT,ENTER", () -> sendKeys(new short[]{KeyboardTranslator.VK_MENU, KeyboardTranslator.VK_RETURN})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                "WIN", () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                "WIN,D", () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                "WIN,G", () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                "SHIFT,TAB", () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));
        customSendKeyOptions.add(new MenuOption(getString(R.string.game_menu_send_move_window),
                "WIN,SHIFT,RIGHT", () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_RIGHT})));
    }

    private void showCustomKeyDialog(@Nullable Integer editIndex, @Nullable String initialLabel, @Nullable String initialKeys) {
        LinearLayout layout = new LinearLayout(game);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        EditText labelInput = new EditText(game);
        labelInput.setHint("Action label (e.g., Send Ctrl+P)");
        labelInput.setText(initialLabel != null ? initialLabel : "");
        layout.addView(labelInput);

        EditText keysInput = new EditText(game);
        keysInput.setHint("Key names (e.g., CTRL,P)");
        keysInput.setText(initialKeys != null ? initialKeys : "");
        layout.addView(keysInput);

        String dialogTitle = (editIndex != null) ? "Edit Custom Key" : "Add Custom Key Action";

        new AlertDialog.Builder(game)
                .setTitle(dialogTitle)
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String label = labelInput.getText().toString().trim();
                    String keys = keysInput.getText().toString().trim();

                    if (!label.isEmpty() && !keys.isEmpty()) {
                        short[] translatedKeys = parseKeyNamesToShorts(keys);
                        if (translatedKeys != null) {
                            if (editIndex != null) {
                                // Edit existing
                                customSendKeyOptions.set(editIndex, new MenuOption(label, keys, () -> sendKeys(translatedKeys)));
                                Toast.makeText(game, "Updated.", Toast.LENGTH_SHORT).show();
                            } else {
                                // Add new
                                addCustomSendKey(label, keys, translatedKeys);
                                Toast.makeText(game, "Key action added.", Toast.LENGTH_SHORT).show();
                            }
                            saveCustomSendKeys();
                            showSpecialKeysMenu();
                        } else {
                            Toast.makeText(game, "Invalid keys.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddCustomKeyDialog() {
        showCustomKeyDialog(null, null, null);
    }

    private void showEditDialog(int index, String oldLabel) {
        MenuOption option = customSendKeyOptions.get(index);
        showCustomKeyDialog(index, option.label, option.keysString);
    }

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>(customSendKeyOptions);

        options.add(new MenuOption("➕ Add Custom Key Action", this::showAddCustomKeyDialog));
        options.add(new MenuOption("♻️ Restore Default Keys", () -> {
            addDefaultKeys();
            saveCustomSendKeys();
            showSpecialKeysMenu();
            Toast.makeText(game, "Default keys restored.", Toast.LENGTH_SHORT).show();
        }));

        showSidebarMenu(getString(R.string.game_menu_send_keys),
                options.toArray(new MenuOption[0]),
                true);
    }

    private void showMenu() {
        List<MenuOption> options = new ArrayList<>();
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, () -> game.toggleKeyboard()));

        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }

        options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> showSpecialKeysMenu()));

        List<MenuOption> configOptions = new ArrayList<>();
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_performance_overlay), () -> game.togglePerformanceOverlay()));
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_controller), () -> game.toggleVirtualController()));
        configOptions.add(new MenuOption(getString(R.string.game_menu_toggle_trackpad), () -> game.toggleTouchscreenTrackpad()));

        options.add(new MenuOption(getString(R.string.game_menu_config), () -> showSidebarMenu("Config", configOptions.toArray(new MenuOption[0]), true)));

        showSidebarMenu("Game Menu", options.toArray(new MenuOption[0]), false); // Pass 'false' since this is not a submenu
    }

}
