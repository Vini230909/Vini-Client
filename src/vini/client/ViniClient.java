package vini.client;

import mindustry.mod.Mod;
import vini.client.bridgeconveyor.BridgePathFeature;
import vini.client.turretfill.TurretFillFeature;

public class ViniClient extends Mod {
    private final ViniFeature[] features = {
        new TurretFillFeature(),
        new BridgePathFeature()
    };

    public ViniClient() {
        for(ViniFeature feature : features){
            feature.init();
        }
    }

    @Override
    public void loadContent() {
        for(ViniFeature feature : features){
            feature.loadContent();
        }
    }
}