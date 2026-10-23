package com.petey.addon;

import com.petey.addon.modules.PeteysEspBypass;
import com.petey.addon.modules.PeteysStaffLive;
import net.anubis.client.addon.AnubisAddon;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;

public final class PeteyAddon extends AnubisAddon {

    public static ModuleCategory CATEGORY;

    @Override
    public void onRegisterCategories() {
        CATEGORY = ModuleCategory.register("PETEY", "Petey");
    }

    @Override
    public void onInitialize() {
        ModuleRegistry.add((ClientModule) new PeteysEspBypass());
        ModuleRegistry.add((ClientModule) new PeteysStaffLive());
    }

    @Override
    public String getPackage() {
        return "com.petey.addon";
    }
}
