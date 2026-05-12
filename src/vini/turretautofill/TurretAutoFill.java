package vini.turretautofill;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Tex;
import mindustry.mod.Mod;

public class TurretAutoFill extends Mod {
    private boolean enabled = false;

    public TurretAutoFill() {
        Log.info("TurretAutoFill loaded.");

        TAFKeybinds.load();

        Events.run(EventType.Trigger.update, () -> {
            if(Core.input.keyTap(TAFKeybinds.toggle)){
                enabled = !enabled;

                if(enabled){
                    showToast("Auto Fill: [lightgray]Enabled");
                }else{
                    showToast("Auto Fill: [scarlet]Disabled");
                }

                Log.info("TurretAutoFill: " + (enabled ? "Enabled" : "Disabled"));
            }
        });
    }

    private void showToast(String text) {
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        Table toast = new Table(Tex.button);
        toast.touchable = arc.scene.event.Touchable.disabled;
        toast.add(new Label(text)).pad(4f, 10f, 4f, 10f);
        toast.pack();

        float screenW = Core.scene.getWidth();
        float screenH = Core.scene.getHeight();

        float centerX = screenW / 2f;

        float startCenterY = screenH - 150f;
        float visibleCenterY = screenH - 200f;

        float x = centerX - toast.getWidth() / 2f;
        float startY = startCenterY - toast.getHeight() / 2f;
        float visibleY = visibleCenterY - toast.getHeight() / 2f;

        toast.setPosition(x, startY);
        toast.color.a = 0f;

        Vars.ui.hudGroup.addChild(toast);
        toast.toFront();

        toast.actions(
            Actions.sequence(
                Actions.parallel(
                    Actions.alpha(1f, 0.30f, Interp.pow3Out),
                    Actions.moveTo(x, visibleY, 0.30f, Interp.pow3Out)
                ),
                Actions.delay(0.2f),
                Actions.parallel(
                    Actions.alpha(0f, 0.35f, Interp.pow3In),
                    Actions.moveTo(x, startY, 0.35f, Interp.pow3In)
                ),
                Actions.remove()
            )
        );
    }

    @Override
    public void loadContent() {
        Log.info("TurretAutoFill content loaded.");
    }
}