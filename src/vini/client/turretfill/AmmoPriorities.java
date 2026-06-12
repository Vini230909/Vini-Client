package vini.client.turretfill;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;

public class AmmoPriorities {
    private static final String SETTING_PREFIX = "vini-turretfill-ammo-priority-";
    private static final String LEGACY_SETTING_PREFIX = "taf-ammo-priority-";

    private static final float ICON_SIZE = 30f;
    private static final float BOX_SIZE = 46f;
    private static final float ROW_HEIGHT = 48f;
    private static final float REMOVE_GAP = 34f;

    private static final Color ACTIVE_BG = Color.valueOf("262626");
    private static final Color ACTIVE_BORDER = Color.valueOf("8d8d8d");
    private static final Color REMOVED_BG = Color.valueOf("2b1111");
    private static final Color REMOVED_BORDER = Color.valueOf("b84747");

    private static Table dragGhost;

    public static void addSettingsCategory() {
        Vars.ui.settings.addCategory("Vini Client", Icon.settings, table -> {
            table.pref(new SettingsTable.Setting("ammo-priority-editor") {
                @Override
                public void add(SettingsTable table) {
                    buildEditor(table);
                }
            });
        });
    }

    private static void buildEditor(SettingsTable table) {
        float editorWidth = Math.max(720f, Math.min(1040f, Core.graphics.getWidth() * 0.68f));

        TextField search = new TextField("");
        search.setMessageText("Search turret or ammo...");

        Table list = new Table();
        list.top().left();

        ScrollPane pane = new ScrollPane(list);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, true);

        final Runnable[] rebuild = new Runnable[1];

        rebuild[0] = () -> {
            list.clear();

            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();

            for(Block block : Vars.content.blocks()){
                if(!(block instanceof ItemTurret)) continue;

                Seq<Item> allAmmo = getAllAmmo(block);
                if(allAmmo.isEmpty()) continue;

                if(!matchesSearch(block, allAmmo, query)) continue;

                addTurretRow(list, block, allAmmo, rebuild[0], editorWidth);
            }
        };

        search.changed(() -> Core.app.post(rebuild[0]));

        table.table(container -> {
            container.top().left();

            container.add(search)
                .width(editorWidth)
                .height(50f)
                .padBottom(4f)
                .row();

            container.add(pane)
                .width(editorWidth)
                .height(520f)
                .row();

            container.table(bottom -> {
                bottom.button("Reset to Defaults", Icon.refresh, () -> {
                    resetAll();
                    Core.app.post(rebuild[0]);
                }).size(220f, 50f);
            }).width(editorWidth).padTop(6f).row();
        }).width(editorWidth).padTop(3f).row();

        rebuild[0].run();
    }

    private static boolean matchesSearch(Block block, Seq<Item> allAmmo, String query) {
        if(query == null || query.isEmpty()) return true;

        if(block.localizedName != null && block.localizedName.toLowerCase().contains(query)) return true;
        if(block.name != null && block.name.toLowerCase().contains(query)) return true;

        for(Item item : allAmmo){
            if(item.localizedName != null && item.localizedName.toLowerCase().contains(query)) return true;
            if(item.name != null && item.name.toLowerCase().contains(query)) return true;
        }

        return false;
    }

    private static void addTurretRow(Table list, Block block, Seq<Item> allAmmo, Runnable rebuild, float editorWidth) {
        Seq<Item> active = getActiveAmmo(block);
        Seq<Item> removed = new Seq<>();

        for(Item item : allAmmo){
            if(!active.contains(item)) removed.add(item);
        }

        float activeWidth = Math.max(BOX_SIZE, allAmmo.size * BOX_SIZE);
        float removedWidth = Math.max(BOX_SIZE, allAmmo.size * BOX_SIZE);

        list.table(row -> {
            row.left();

            row.image(block.uiIcon).size(40f).padRight(8f);
            row.add(block.localizedName + ":").width(180f).left().padRight(8f);

            Table activeBar = new Table();
            activeBar.left();
            activeBar.touchable = Touchable.enabled;

            Table removedBar = new Table();
            removedBar.left();
            removedBar.touchable = Touchable.enabled;

            if(active.isEmpty()){
                activeBar.add(createActiveDropCell()).size(BOX_SIZE).padRight(0f);
            }else{
                for(Item item : active){
                    activeBar.add(ammoBox(block, item, false, activeBar, removedBar, rebuild))
                        .size(BOX_SIZE)
                        .padRight(0f);
                }
            }

            if(removed.isEmpty()){
                removedBar.add(createRemovedDropCell()).size(BOX_SIZE).padRight(0f);
            }else{
                for(Item item : removed){
                    removedBar.add(ammoBox(block, item, true, activeBar, removedBar, rebuild))
                        .size(BOX_SIZE)
                        .padRight(0f);
                }
            }

            row.add(activeBar)
                .width(activeWidth)
                .height(BOX_SIZE)
                .left();

            row.add().width(REMOVE_GAP);

            row.add(removedBar)
                .width(removedWidth)
                .height(BOX_SIZE)
                .left();

        }).width(editorWidth).height(ROW_HEIGHT).left().padBottom(2f).row();
    }

    private static Table createActiveDropCell() {
        AmmoCell cell = new AmmoCell(false);
        cell.touchable = Touchable.enabled;

        cell.add().size(ICON_SIZE);

        return cell;
    }

    private static Table createRemovedDropCell() {
        AmmoCell cell = new AmmoCell(true);
        cell.touchable = Touchable.enabled;

        cell.add().size(ICON_SIZE);

        return cell;
    }

    private static Table ammoBox(Block block, Item item, boolean removed, Table activeBar, Table removeBox, Runnable rebuild) {
        AmmoCell box = new AmmoCell(removed);
        box.touchable = Touchable.enabled;

        box.image(item.uiIcon).size(ICON_SIZE);

        if(removed){
            box.color.a = 0.9f;
        }

        box.addListener(new InputListener(){
            private boolean dragging = false;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft) return false;

                dragging = true;
                startGhost(item, removed);
                updateGhost();
                box.color.a = 0.42f;
                event.stop();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(!dragging) return;

                updateGhost();
                event.stop();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(!dragging) return;
                event.stop();

                dragging = false;
                box.color.a = removed ? 0.9f : 1f;

                Vec2 pos = mouseStage();
                float stageX = pos.x;
                float stageY = pos.y;

                stopGhost();

                if(containsStage(removeBox, stageX, stageY)){
                    removeAmmo(block, item);
                    Core.app.post(rebuild);
                    return;
                }

                if(containsStage(activeBar, stageX, stageY)){
                    int index = indexForStageX(activeBar, stageX);
                    moveToIndex(block, item, index);
                    Core.app.post(rebuild);
                }
            }
        });

        return box;
    }

    private static Vec2 mouseStage() {
        return Core.scene.screenToStageCoordinates(new Vec2(Core.input.mouseX(), Core.input.mouseY()));
    }

    private static void startGhost(Item item, boolean removed) {
        stopGhost();

        dragGhost = new AmmoCell(removed);
        dragGhost.touchable = Touchable.disabled;
        dragGhost.image(item.uiIcon).size(ICON_SIZE);
        dragGhost.pack();
        dragGhost.color.a = 0.95f;

        Core.scene.root.addChild(dragGhost);
        dragGhost.toFront();
    }

    private static void updateGhost() {
        if(dragGhost == null) return;

        Vec2 pos = mouseStage();

        dragGhost.setPosition(
            pos.x - dragGhost.getWidth() / 2f,
            pos.y - dragGhost.getHeight() / 2f
        );

        dragGhost.toFront();
    }

    private static void stopGhost() {
        if(dragGhost != null){
            dragGhost.remove();
            dragGhost = null;
        }
    }

    private static boolean containsStage(Element element, float stageX, float stageY) {
        if(element == null) return false;

        Vec2 pos = new Vec2(0f, 0f);
        element.localToStageCoordinates(pos);

        return stageX >= pos.x &&
            stageX <= pos.x + element.getWidth() &&
            stageY >= pos.y &&
            stageY <= pos.y + element.getHeight();
    }

    private static int indexForStageX(Element element, float stageX) {
        Vec2 pos = new Vec2(0f, 0f);
        element.localToStageCoordinates(pos);

        float rel = stageX - pos.x;
        int index = (int)(rel / BOX_SIZE);

        return Math.max(0, index);
    }

    public static Seq<Item> getActiveAmmo(Block block) {
        Seq<Item> allAmmo = getAllAmmo(block);
        Seq<Item> result = new Seq<>();

        String raw = Core.settings.getString(key(block), null);

        if(raw == null){
            raw = Core.settings.getString(legacyKey(block), null);
        }

        if(raw == null){
            return allAmmo;
        }

        if(!raw.isEmpty()){
            String[] parts = raw.split(",");

            for(String part : parts){
                Item item = findItem(part.trim());
                if(item != null && allAmmo.contains(item) && !result.contains(item)){
                    result.add(item);
                }
            }
        }

        return result;
    }

    private static Seq<Item> getAllAmmo(Block block) {
        Seq<Item> ammo = new Seq<>();

        if(!(block instanceof ItemTurret)) return ammo;

        ItemTurret turret = (ItemTurret)block;

        for(Item item : turret.ammoTypes.keys()){
            if(item != null && !ammo.contains(item)){
                ammo.add(item);
            }
        }

        ammo.sort((a, b) -> Float.compare(defaultScore(turret, b), defaultScore(turret, a)));

        return ammo;
    }

    private static float defaultScore(ItemTurret turret, Item item) {
        if(turret.ammoTypes.get(item) == null) return 0f;
        return turret.ammoTypes.get(item).estimateDPS();
    }

    private static void moveToIndex(Block block, Item item, int index) {
        Seq<Item> active = getActiveAmmo(block);
        Seq<Item> allAmmo = getAllAmmo(block);

        if(!allAmmo.contains(item)) return;

        active.remove(item);

        index = Mathf.clamp(index, 0, active.size);

        active.insert(index, item);
        save(block, active);
    }

    private static void removeAmmo(Block block, Item item) {
        Seq<Item> active = getActiveAmmo(block);
        active.remove(item);
        save(block, active);
    }

    private static void save(Block block, Seq<Item> active) {
        StringBuilder builder = new StringBuilder();

        for(int i = 0; i < active.size; i++){
            if(i > 0) builder.append(",");
            builder.append(active.get(i).name);
        }

        Core.settings.put(key(block), builder.toString());
    }

    private static void resetAll() {
        for(Block block : Vars.content.blocks()){
            if(block instanceof ItemTurret){
                Core.settings.remove(key(block));
                Core.settings.remove(legacyKey(block));
            }
        }
    }

    private static String key(Block block) {
        return SETTING_PREFIX + block.name;
    }

    private static String legacyKey(Block block) {
        return LEGACY_SETTING_PREFIX + block.name;
    }

    private static Item findItem(String name) {
        if(name == null || name.isEmpty()) return null;

        for(Item item : Vars.content.items()){
            if(item.name.equals(name)) return item;
        }

        return null;
    }

    private static class AmmoCell extends Table {
        private final boolean removed;

        AmmoCell(boolean removed) {
            this.removed = removed;
            setTransform(false);
        }

        @Override
        public void draw() {
            validate();

            float alpha = color.a * parentAlpha;
            Color bg = removed ? REMOVED_BG : ACTIVE_BG;
            Color border = removed ? REMOVED_BORDER : ACTIVE_BORDER;

            Draw.color(bg.r, bg.g, bg.b, alpha);
            Fill.rect(x + width / 2f, y + height / 2f, width, height);

            Draw.color(border.r, border.g, border.b, alpha);
            Lines.stroke(2f);
            Lines.rect(x, y, width, height);

            Draw.color();
            super.draw();
        }
    }
}
