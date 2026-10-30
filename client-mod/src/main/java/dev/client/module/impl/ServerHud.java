package dev.client.module.impl;

import dev.client.gui.ClickGui;
import dev.client.gui.Render2D;
import dev.client.module.BoolSetting;
import dev.client.module.HudModule;
import dev.client.module.Module;
import dev.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;

/** Shows the Server Helper binds with the key and how many of each item are left. */
public class ServerHud
extends HudModule {
    private static final int BG = -586018282;
    private static final int GREEN = -11751600;
    private static final int AMBER = -16121;
    private static final int RED = -769226;
    private static final int DIM = -5592406;

    private final BoolSetting showUnbound = this.add(new BoolSetting("Show unbound", false));
    private final BoolSetting showKey = this.add(new BoolSetting("Show key", true));

    public ServerHud() {
        // x = -1 means "stick to the right edge" until the block is dragged somewhere
        super("Server HUD", -1, 40, true);
        this.w = 120;
    }

    @Override
    public void render(MatrixStack ms) {
        ServerHelper helper = null;
        for (Module m : ModuleManager.modules) {
            if (m instanceof ServerHelper) {
                helper = (ServerHelper)m;
            }
        }
        List<ServerHelper.Info> rows = helper == null ? new ArrayList<ServerHelper.Info>() : helper.info(this.showUnbound.value);
        boolean preview = rows.isEmpty() && ServerHud.mc.currentScreen instanceof ClickGui;
        if (rows.isEmpty() && !preview) {
            return;
        }
        List<String> names = new ArrayList<String>();
        List<String> keys = new ArrayList<String>();
        List<Integer> counts = new ArrayList<Integer>();
        if (preview) {
            names.add("Bind items");
            keys.add("[-]");
            counts.add(0);
        }
        for (ServerHelper.Info r : rows) {
            names.add(r.name);
            keys.add("[" + r.key + "]");
            counts.add(r.count);
        }

        String title = "Server";
        int width = ServerHud.mc.textRenderer.getWidth(title);
        for (int i = 0; i < names.size(); ++i) {
            int rw = ServerHud.mc.textRenderer.getWidth(names.get(i)) + ServerHud.mc.textRenderer.getWidth("x" + counts.get(i)) + 22;
            if (this.showKey.value) {
                rw += ServerHud.mc.textRenderer.getWidth(keys.get(i)) + 6;
            }
            width = Math.max(width, rw);
        }
        this.w = width + 8;
        this.h = 14 + names.size() * 11;
        if (this.x < 0) {
            this.x = ServerHud.mc.getWindow().getScaledWidth() - this.w - 6;
        }

        Render2D.shadow(ms, 5.0f, this.x, this.y, this.x + this.w, this.y + this.h, 0.9f);
        Render2D.rrect(ms, 5.0f, this.x, this.y, this.x + this.w, this.y + this.h, BG);
        ServerHud.mc.textRenderer.drawWithShadow(ms, title, (float)(this.x + 4), (float)(this.y + 3), -1);

        int ly = this.y + 15;
        for (int i = 0; i < names.size(); ++i) {
            int n = counts.get(i);
            int col = n <= 0 ? RED : (n <= 2 ? AMBER : GREEN);
            Render2D.rrect(ms, 1.0f, this.x + 4, ly + 1, this.x + 6, ly + 8, col);
            int nameColor = n <= 0 ? DIM : -1;
            ServerHud.mc.textRenderer.drawWithShadow(ms, names.get(i), (float)(this.x + 10), (float)ly, nameColor);
            String cnt = "x" + n;
            int cx = this.x + this.w - 4 - ServerHud.mc.textRenderer.getWidth(cnt);
            ServerHud.mc.textRenderer.drawWithShadow(ms, cnt, (float)cx, (float)ly, col);
            if (this.showKey.value) {
                String k = keys.get(i);
                ServerHud.mc.textRenderer.drawWithShadow(ms, k, (float)(cx - 6 - ServerHud.mc.textRenderer.getWidth(k)), (float)ly, DIM);
            }
            ly += 11;
        }
    }
}
