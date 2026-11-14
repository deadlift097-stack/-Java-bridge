package dev.example.hello;

import com.modhost.api.EventHandler;
import com.modhost.api.Events;
import com.modhost.api.ModContext;
import com.modhost.api.ModHostMod;

public class HelloMod implements ModHostMod {
    private ModContext ctx;
    private int ticks;

    public void onLoad(ModContext ctx) {
        this.ctx = ctx;
        ctx.logger.info("Hello from Java! data dir: " + ctx.dataDir);

        // register handlers in onLoad; they are skipped automatically while the mod is disabled
        Events.onTick(new Runnable() {
            public void run() {
                if (++ticks % 1200 == 0) HelloMod.this.ctx.logger.info("1 minute of ticks passed");
            }
        });
        Events.on("app.resumed", new EventHandler() {
            public void handle(String name, String data) {
                HelloMod.this.ctx.logger.info("game screen resumed: " + data);
            }
        });
        // custom event: any mod can invent and post its own
        Events.on("hello.custom", new EventHandler() {
            public void handle(String name, String data) {
                HelloMod.this.ctx.logger.info("custom event received: " + data);
            }
        });
    }

    public void onEnable() {
        ctx.logger.info("enabled");
        Events.post("hello.custom", "posted from onEnable");
    }

    public void onDisable() { ctx.logger.info("disabled"); }
}
