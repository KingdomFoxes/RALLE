package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.client.Minecraft;

/** Optional-integration port whose signature never exposes Wynntils classes. */
interface QueueAttributionAdapter {
    boolean ready();
    void register();
    void unregister();
    boolean registered();
    void tick(Minecraft minecraft);
    void decorateTimer(Object timer, Object renderTask);
    void decoratePreview(Object renderTask);
    void clear();
}
