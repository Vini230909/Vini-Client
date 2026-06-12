package vini.client.turretfill;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import vini.client.ViniFeature;

public class TurretFillFeature implements ViniFeature {
    private static final boolean DEBUG = false;

    private static final float TRANSFER_DELAY = 10f;
    private static final float TRANSFER_CONFIRM_TIMEOUT = 12f;

    private static final float PICKUP_DELAY = 20f;
    private static final float PICKUP_CONFIRM_TIMEOUT = 30f;

    private static final float DROP_DELAY = 10f;
    private static final float DROP_CONFIRM_TIMEOUT = 20f;

    private static final int MIN_TRANSFER = 1;
    private static final int MIN_CORE_ITEMS = 1;

    private boolean enabled = false;

    private float transferTimer = 0f;
    private float pickupTimer = 0f;
    private float dropTimer = 0f;

    private boolean waitingForTransfer = false;
    private float transferConfirmTimer = 0f;
    private int heldAmountBeforeTransfer = 0;

    private boolean waitingForPickup = false;
    private float pickupConfirmTimer = 0f;

    private boolean waitingForCoreDrop = false;
    private float dropConfirmTimer = 0f;
    private Item dropItemBefore = null;
    private int dropAmountBefore = 0;

    private boolean coreSessionActive = false;
    private boolean coreSessionDone = false;
    private Item originalItem = null;
    private boolean restoringOriginalItem = false;

    private int lastTurretCount = 0;
    private int lastCompatibleCount = 0;
    private int lastFilledCount = 0;
    private int lastPickupCount = 0;
    private int lastDropCount = 0;

    private String lastPickupItem = "None";
    private String lastOriginalItem = "None";
    private boolean lastCoreInRange = false;

    private final Seq<Building> builds = new Seq<>(false);

    private Table debugTable;
    private Label debugLabel;

    @Override
    public void init() {
        Log.info("Turret Fill feature loaded.");

        TurretFillKeybinds.load();

        Events.on(EventType.ClientLoadEvent.class, event -> {
            createDebugUi();
            AmmoPriorities.addSettingsCategory();
        });

        Events.run(EventType.Trigger.update, () -> {
            if(Core.input.keyTap(TurretFillKeybinds.toggle)){
                enabled = !enabled;

                resetRuntimeState();

                if(enabled){
                    showToast("Turret Fill: [lightgray]Enabled");
                }else{
                    showToast("Turret Fill: [scarlet]Disabled");
                }

                Log.info("Turret Fill: " + (enabled ? "Enabled" : "Disabled"));
            }

            updateAutoFill();
            updateDebugUi();
        });
    }

    private void resetRuntimeState() {
        waitingForTransfer = false;
        waitingForPickup = false;
        waitingForCoreDrop = false;

        transferConfirmTimer = 0f;
        pickupConfirmTimer = 0f;
        dropConfirmTimer = 0f;

        coreSessionActive = false;
        coreSessionDone = false;
        originalItem = null;
        restoringOriginalItem = false;

        lastFilledCount = 0;
        lastPickupCount = 0;
        lastDropCount = 0;
        lastPickupItem = "None";
        lastOriginalItem = "None";
    }

    private void createDebugUi() {
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        debugTable = new Table(Tex.button);
        debugTable.touchable = Touchable.disabled;
        debugTable.visible = false;

        debugLabel = new Label("");
        debugTable.add(debugLabel).pad(6f, 10f, 6f, 10f);

        Vars.ui.hudGroup.addChild(debugTable);
    }

    private void updateAutoFill() {
        if(!enabled || !Vars.state.isGame() || Vars.player == null || Vars.player.dead()){
            return;
        }

        Unit unit = Vars.player.unit();

        if(unit == null){
            return;
        }

        Building core = getCoreInRange();
        lastCoreInRange = core != null;

        if(core != null){
            updateCoreRangeMode(unit, core);
        }else{
            resetCoreSessionOnly();

            Item heldItem = unit.item();
            int heldAmount = unit.stack.amount;

            if(heldItem != null && heldAmount > 0){
                updateHeldAmmoTransfer(unit, heldItem, heldAmount, false, null);
            }
        }
    }

    private void resetCoreSessionOnly() {
        coreSessionActive = false;
        coreSessionDone = false;
        originalItem = null;
        restoringOriginalItem = false;
        lastOriginalItem = "None";

        waitingForPickup = false;
        waitingForCoreDrop = false;
        pickupConfirmTimer = 0f;
        dropConfirmTimer = 0f;
    }

    private void updateCoreRangeMode(Unit unit, Building core) {
        if(coreSessionDone){
            if(findBestCoreWantedItem(unit, core) == null){
                return;
            }

            coreSessionDone = false;
        }

        if(!coreSessionActive){
            originalItem = unit.item();
            lastOriginalItem = originalItem == null ? "None" : originalItem.localizedName;
            coreSessionActive = true;
            restoringOriginalItem = false;
        }

        if(waitingForCoreDrop){
            dropConfirmTimer += Time.delta;

            Item current = unit.item();
            int amount = unit.stack.amount;

            if(current == null || amount <= 0 || current != dropItemBefore || amount < dropAmountBefore || dropConfirmTimer >= DROP_CONFIRM_TIMEOUT){
                waitingForCoreDrop = false;
                dropConfirmTimer = 0f;
                dropItemBefore = null;
                dropAmountBefore = 0;
            }else{
                return;
            }
        }

        if(waitingForPickup){
            pickupConfirmTimer += Time.delta;

            if(unit.item() != null || pickupConfirmTimer >= PICKUP_CONFIRM_TIMEOUT){
                waitingForPickup = false;
                pickupConfirmTimer = 0f;
            }else{
                return;
            }
        }

        Item heldItem = unit.item();
        int heldAmount = unit.stack.amount;

        if(heldItem != null && heldAmount > 0){
            if(hasCoreModeTargetsForItem(unit, core, heldItem)){
                updateHeldAmmoTransfer(unit, heldItem, heldAmount, true, core);
                return;
            }

            Item wantedItem = findBestCoreWantedItem(unit, core);

            if(wantedItem == null){
                finishCoreSession(unit, core);
            }else{
                dropHeldItemToCore(unit, core);
            }

            return;
        }

        Item wantedItem = findBestCoreWantedItem(unit, core);

        if(wantedItem == null){
            finishCoreSession(unit, core);
            return;
        }

        requestItemFromCore(core, wantedItem);
    }

    private boolean hasCoreModeTargetsForItem(Unit unit, Building core, Item item) {
        if(item == null) return false;
        if(Vars.player.team() == null || Vars.player.team().data() == null || Vars.player.team().data().buildingTree == null) return false;

        float range = Vars.itemTransferRange;
        float px = Vars.player.x;
        float py = Vars.player.y;

        builds.clear();

        Vars.player.team().data().buildingTree.intersect(
            px - range,
            py - range,
            range * 2f,
            range * 2f,
            builds
        );

        for(Building build : builds){
            if(build == null) continue;
            if(build.team != Vars.player.team()) continue;
            if(!(build.block instanceof ItemTurret)) continue;
            if(!Vars.player.within(build, range)) continue;
            if(!build.block.consumesItem(item)) continue;

            Item bestAmmo = bestAvailableAmmoForTurret(build, unit, core);
            if(bestAmmo != item) continue;

            int accepted = build.acceptStack(item, Integer.MAX_VALUE, unit);
            if(accepted >= MIN_TRANSFER){
                return true;
            }
        }

        return false;
    }

    private void finishCoreSession(Unit unit, Building core) {
        Item heldItem = unit.item();
        int heldAmount = unit.stack.amount;

        if(heldItem != null && heldAmount > 0){
            if(restoringOriginalItem && originalItem != null && heldItem == originalItem){
                restoringOriginalItem = false;
                coreSessionActive = false;
                coreSessionDone = true;
                originalItem = null;
                lastOriginalItem = "None";
                return;
            }

            if(originalItem != null && heldItem == originalItem){
                coreSessionActive = false;
                coreSessionDone = true;
                originalItem = null;
                lastOriginalItem = "None";
                return;
            }

            dropHeldItemToCore(unit, core);
            return;
        }

        if(originalItem != null && core.items != null && core.items.has(originalItem, MIN_CORE_ITEMS)){
            restoringOriginalItem = true;
            requestItemFromCore(core, originalItem);
            return;
        }

        coreSessionActive = false;
        coreSessionDone = true;
        originalItem = null;
        lastOriginalItem = "None";
        restoringOriginalItem = false;
    }

    private void dropHeldItemToCore(Unit unit, Building core) {
        Item heldItem = unit.item();
        int heldAmount = unit.stack.amount;

        if(heldItem == null || heldAmount <= 0) return;

        dropTimer += Time.delta;

        if(dropTimer < DROP_DELAY){
            return;
        }

        dropTimer = 0f;

        Call.transferInventory(Vars.player, core);

        waitingForCoreDrop = true;
        dropConfirmTimer = 0f;
        dropItemBefore = heldItem;
        dropAmountBefore = heldAmount;
        lastDropCount++;
    }

    private void requestItemFromCore(Building core, Item item) {
        if(item == null) return;
        if(core.items == null || !core.items.has(item, MIN_CORE_ITEMS)) return;

        pickupTimer += Time.delta;

        if(pickupTimer < PICKUP_DELAY){
            return;
        }

        pickupTimer = 0f;

        Call.requestItem(Vars.player, core, item, Integer.MAX_VALUE);

        lastPickupCount++;
        lastPickupItem = item.localizedName;

        waitingForPickup = true;
        pickupConfirmTimer = 0f;
    }

    private void updateHeldAmmoTransfer(Unit unit, Item heldItem, int heldAmount, boolean coreMode, Building core) {
        if(waitingForTransfer){
            transferConfirmTimer += Time.delta;

            if(unit.item() == null || unit.stack.amount < heldAmountBeforeTransfer || transferConfirmTimer >= TRANSFER_CONFIRM_TIMEOUT){
                waitingForTransfer = false;
                transferConfirmTimer = 0f;
            }else{
                return;
            }
        }

        if(Vars.player.team() == null || Vars.player.team().data() == null || Vars.player.team().data().buildingTree == null){
            return;
        }

        float range = Vars.itemTransferRange;
        float px = Vars.player.x;
        float py = Vars.player.y;

        lastTurretCount = 0;
        lastCompatibleCount = 0;

        builds.clear();

        Vars.player.team().data().buildingTree.intersect(
            px - range,
            py - range,
            range * 2f,
            range * 2f,
            builds
        );

        Building bestTarget = null;
        int bestCurrentAmount = Integer.MAX_VALUE;

        for(Building build : builds){
            if(build == null) continue;
            if(build.team != Vars.player.team()) continue;
            if(!(build.block instanceof ItemTurret)) continue;
            if(!Vars.player.within(build, range)) continue;

            lastTurretCount++;

            if(!build.block.consumesItem(heldItem)) continue;

            if(coreMode){
                Item bestAmmo = bestAvailableAmmoForTurret(build, unit, core);
                if(bestAmmo != heldItem) continue;
            }

            int accepted = build.acceptStack(heldItem, heldAmount, unit);
            if(accepted < MIN_TRANSFER) continue;

            lastCompatibleCount++;

            int currentAmount = build.items == null ? 0 : build.items.get(heldItem);

            if(bestTarget == null || currentAmount < bestCurrentAmount){
                bestTarget = build;
                bestCurrentAmount = currentAmount;
            }
        }

        transferTimer += Time.delta;

        if(bestTarget == null){
            return;
        }

        if(transferTimer < TRANSFER_DELAY){
            return;
        }

        transferTimer = 0f;

        Call.transferInventory(Vars.player, bestTarget);
        lastFilledCount++;

        waitingForTransfer = true;
        transferConfirmTimer = 0f;
        heldAmountBeforeTransfer = heldAmount;
    }

    private Item findBestCoreWantedItem(Unit unit, Building core) {
        if(Vars.player.team() == null || Vars.player.team().data() == null || Vars.player.team().data().buildingTree == null){
            return null;
        }

        int itemCount = Vars.content.items().size;
        float[] itemScores = new float[itemCount];

        float range = Vars.itemTransferRange;
        float px = Vars.player.x;
        float py = Vars.player.y;

        lastTurretCount = 0;
        lastCompatibleCount = 0;

        builds.clear();

        Vars.player.team().data().buildingTree.intersect(
            px - range,
            py - range,
            range * 2f,
            range * 2f,
            builds
        );

        for(Building build : builds){
            if(build == null) continue;
            if(build.team != Vars.player.team()) continue;
            if(!(build.block instanceof ItemTurret)) continue;
            if(!Vars.player.within(build, range)) continue;

            lastTurretCount++;

            Item bestAmmo = bestAvailableAmmoForTurret(build, unit, core);

            if(bestAmmo == null) continue;

            int accepted = build.acceptStack(bestAmmo, Integer.MAX_VALUE, unit);
            if(accepted < MIN_TRANSFER) continue;

            itemScores[bestAmmo.id] += 1000f + accepted;
            lastCompatibleCount++;
        }

        int bestId = -1;
        float bestScore = 0f;

        for(int i = 0; i < itemScores.length; i++){
            if(itemScores[i] > bestScore){
                bestScore = itemScores[i];
                bestId = i;
            }
        }

        if(bestId == -1){
            return null;
        }

        return Vars.content.item(bestId);
    }

    private Item bestAvailableAmmoForTurret(Building build, Unit unit, Building core) {
        if(build == null || !(build.block instanceof ItemTurret)) return null;

        Seq<Item> priority = AmmoPriorities.getActiveAmmo(build.block);
        Item heldItem = unit.item();

        for(Item item : priority){
            if(item == null) continue;
            if(!build.block.consumesItem(item)) continue;

            boolean availableInCore = core != null && core.items != null && core.items.has(item, MIN_CORE_ITEMS);
            boolean availableInUnit = heldItem == item && unit.stack.amount > 0;

            if(!availableInCore && !availableInUnit) continue;

            int accepted = build.acceptStack(item, Integer.MAX_VALUE, unit);
            if(accepted < MIN_TRANSFER) continue;

            return item;
        }

        return null;
    }

    private Building getCoreInRange() {
        if(Vars.player == null) return null;

        Building core = Vars.player.closestCore();

        if(core == null) return null;
        if(!Vars.player.within(core, Vars.itemTransferRange)) return null;

        return core;
    }

    private void updateDebugUi() {
        if(!DEBUG){
            if(debugTable != null) debugTable.visible = false;
            return;
        }

        if(debugTable == null || debugLabel == null) return;

        if(!enabled || !Vars.state.isGame() || Vars.player == null || Vars.player.dead()){
            debugTable.visible = false;
            return;
        }

        Unit unit = Vars.player.unit();

        if(unit == null){
            debugTable.visible = false;
            return;
        }

        Item item = unit.item();
        String held = item == null ? "None" : item.localizedName;
        int amount = unit.stack.amount;

        debugLabel.setText(
            "Turret Fill: [lightgray]ON\n" +
            "Turrets: [lightgray]" + lastTurretCount + "\n" +
            "Compatible: [lightgray]" + lastCompatibleCount + "\n" +
            "Filled: [lightgray]" + lastFilledCount + "\n" +
            "Core: " + (lastCoreInRange ? "[lightgray]Yes" : "[scarlet]No") + "\n" +
            "Pickup: [lightgray]" + lastPickupItem + " x" + lastPickupCount + "\n" +
            "Drops: [lightgray]" + lastDropCount + "\n" +
            "Original: [lightgray]" + lastOriginalItem + "\n" +
            "Held: [lightgray]" + held + " x" + amount
        );

        debugTable.pack();

        float screenW = Core.scene.getWidth();
        float screenH = Core.scene.getHeight();

        float rightPadding = 50f;
        float x = screenW - debugTable.getWidth() - rightPadding;
        float y = screenH / 2f - debugTable.getHeight() / 2f;

        debugTable.setPosition(x, y);
        debugTable.visible = true;
        debugTable.toFront();
    }

    private void showToast(String text) {
        if(Vars.ui == null || Vars.ui.hudGroup == null) return;

        Table toast = new Table(Tex.button);
        toast.touchable = Touchable.disabled;
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
        Log.info("Turret Fill content loaded.");
    }
}
