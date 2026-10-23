package com.petey.addon.maps;

import com.petey.addon.PeteyAddon;
import com.petey.addon.modules.PeteysRegionMap;
import com.petey.addon.modules.PeteysSeedMap;
import net.anubis.client.addon.AnubisAddon;
import net.anubis.client.module.ModuleRegistry;

/** Additional entrypoint; preserves the repository's original addon verbatim. */
public final class PeteyMapsAddon extends AnubisAddon {
    private final PeteyAddon original = new PeteyAddon();

    @Override
    public void onRegisterCategories() {
        original.onRegisterCategories();
    }

    @Override
    public void onInitialize() {
        original.onInitialize();
        ModuleRegistry.add(new PeteysRegionMap());
        ModuleRegistry.add(new PeteysSeedMap());
    }

    @Override
    public void onConfigLoaded() {
        original.onConfigLoaded();
    }

    @Override
    public String getPackage() {
        return "com.petey.addon";
    }
}
