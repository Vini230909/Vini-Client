package vini.client.turretfill;

import arc.input.KeyBind;
import arc.input.KeyCode;

public class TurretFillKeybinds {
    public static final KeyBind toggle = KeyBind.add(
        "toggle-turret-auto-fill",
        KeyCode.f4,
        "Vini Client"
    );

    private TurretFillKeybinds() {
    }

    public static void load() {
        // forces static initialization
    }
}
