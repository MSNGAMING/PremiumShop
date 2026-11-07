package com.premiumshop.plugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import org.jetbrains.annotations.NotNull;

/**
 * Paper Native Plugin Bootstrapper for PremiumShop.
 *
 * <p>Executed early in the server startup sequence prior to world loading
 * and plugin enabling.
 */
public class PremiumShopBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final @NotNull BootstrapContext context) {
        context.getLogger().info("Bootstrapping PremiumShop Native Paper Plugin (Paper 1.21 - 26.3)...");
    }
}
