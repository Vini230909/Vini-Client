package vini.client;

import arc.util.Log;
import mindustry.mod.Mod;
import vini.client.turretfill.TurretFillFeature;

public class ViniClient extends Mod {
    private final ViniFeature[] features = {
        new TurretFillFeature()
    };

    public ViniClient() {
        Log.info("Vini Client loaded.");

        for(ViniFeature feature : features){
            feature.init();
        }
    }

    @Override
    public void loadContent() {
        for(ViniFeature feature : features){
            feature.loadContent();
        }

        Log.info("Vini Client content loaded.");
    }
}
