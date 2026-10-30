package dev.client.module.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.gui.ClickGui;
import dev.client.gui.Render2D;
import dev.client.module.BoolSetting;
import dev.client.module.HudModule;
import dev.client.module.NumSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Target HUD: head, name, smooth health bar with a damage trail, absorption, armor and distance.
 * Drag it with the mouse while the ClickGUI is open (frame shows where it is).
 */
public class TargetHud
extends HudModule {
    private static final int BG = -586018282;
    private static final int A1 = -7643914;
    private static final int A2 = -13058568;
    private static final int GREEN = -11751600;
    private static final int AMBER = -16121;
    private static final int RED = -769226;
    private static final int GOLD = -2380288;
    private static final int DIM = -5592406;
    private static final int TRACK = -13421773;

    private final BoolSetting showHead = this.add(new BoolSetting("Show head", true));
    private final BoolSetting showArmor = this.add(new BoolSetting("Armor", true));
    private final BoolSetting showDist = this.add(new BoolSetting("Distance", true));
    private final BoolSetting playersOnly = this.add(new BoolSetting("Players only", false));
    private final NumSetting linger = this.add(new NumSetting("Linger (s)", 3.0, 1.0, 15.0, 1.0));

    private LivingEntity target;
    private long seen;
    private long lastNs = System.nanoTime();
    private float shownHp = 20.0f;
    private float trailHp = 20.0f;

    public TargetHud() {
        super("Target HUD", 6, 40, true);
        this.w = 150;
        this.h = 40;
    }

    private static int hpColor(float ratio) {
        if (ratio > 0.5f) {
            return Render2D.lerp(AMBER, GREEN, (ratio - 0.5f) * 2.0f);
        }
        return Render2D.lerp(RED, AMBER, ratio * 2.0f);
    }

    @Override
    public void render(MatrixStack ms) {
        long now = System.currentTimeMillis();
        long ns = System.nanoTime();
        float dt = Math.min((float)(ns - this.lastNs) / 1.0E9f, 0.1f);
        this.lastNs = ns;

        HitResult hr = TargetHud.mc.crosshairTarget;
        if (hr instanceof EntityHitResult) {
            Entity e = ((EntityHitResult)hr).getEntity();
            if (e instanceof LivingEntity && e != TargetHud.mc.player && (!this.playersOnly.value || e instanceof PlayerEntity)) {
                if (e != this.target) {
                    this.target = (LivingEntity)e;
                    this.shownHp = this.target.getHealth();
                    this.trailHp = this.shownHp;
                }
                this.seen = now;
            }
        }
        boolean live = this.target != null && this.target.isAlive() && (double)(now - this.seen) < this.linger.value * 1000.0;
        boolean preview = !live && TargetHud.mc.currentScreen instanceof ClickGui;
        if (!live && !preview) {
            return;
        }

        String name = live ? this.target.getName().getString() : "Target";
        float hp = live ? this.target.getHealth() : 20.0f;
        float max = live ? Math.max(this.target.getMaxHealth(), 1.0f) : 20.0f;
        float abs = live ? this.target.getAbsorptionAmount() : 0.0f;
        float dist = live ? TargetHud.mc.player.distanceTo((Entity)this.target) : 0.0f;

        // smooth bar + slow damage trail
        this.shownHp += (hp - this.shownHp) * Math.min(1.0f, dt * 10.0f);
        if (hp > this.trailHp) {
            this.trailHp = hp;
        } else {
            this.trailHp += (hp - this.trailHp) * Math.min(1.0f, dt * 2.5f);
        }
        float ratio = Math.max(0.0f, Math.min(1.0f, this.shownHp / max));
        float trail = Math.max(0.0f, Math.min(1.0f, this.trailHp / max));

        // armor icons (helmet first), only for things that wear any
        List<ItemStack> gear = new ArrayList<ItemStack>();
        if (live && this.showArmor.value) {
            for (ItemStack st : this.target.getArmorItems()) {
                gear.add(st);
            }
            Collections.reverse(gear);
            gear.add(this.target.getMainHandStack());
        } else if (preview && this.showArmor.value) {
            for (int i = 0; i < 5; ++i) {
                gear.add(ItemStack.EMPTY);
            }
        }
        boolean anyGear = false;
        for (ItemStack st : gear) {
            if (!st.isEmpty()) {
                anyGear = true;
            }
        }
        boolean armorRow = this.showArmor.value && (anyGear || preview);

        int headSize = this.showHead.value ? 30 : 0;
        int pad = 5;
        int cx = this.x + pad + (headSize > 0 ? headSize + 6 : 0);
        String distStr = String.format("%.1fm", Float.valueOf(dist));
        int nameW = TargetHud.mc.textRenderer.getWidth(name);
        int need = (cx - this.x) + nameW + pad + 6;
        if (this.showDist.value) {
            need += TargetHud.mc.textRenderer.getWidth(distStr) + 6;
        }
        this.w = Math.max(150, need);
        this.h = 40 + (armorRow ? 19 : 0);

        // card
        Render2D.shadow(ms, 6.0f, this.x, this.y, this.x + this.w, this.y + this.h, 0.9f);
        Render2D.rrect(ms, 6.0f, this.x, this.y, this.x + this.w, this.y + this.h, BG);
        Render2D.rrectH(ms, 1.0f, this.x + 10, this.y, this.x + this.w - 10, this.y + 1.5f, A1, A2);

        // head
        if (headSize > 0) {
            int hx = this.x + pad;
            int hy = this.y + pad;
            Render2D.rrect(ms, 4.0f, hx - 1, hy - 1, hx + headSize + 1, hy + headSize + 1, -14803414);
            if (live && this.target instanceof AbstractClientPlayerEntity) {
                TargetHud.mc.getTextureManager().bindTexture(((AbstractClientPlayerEntity)this.target).getSkinTexture());
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
                DrawableHelper.drawTexture(ms, hx, hy, headSize, headSize, 8.0f, 8.0f, 8, 8, 64, 64);
                DrawableHelper.drawTexture(ms, hx, hy, headSize, headSize, 40.0f, 8.0f, 8, 8, 64, 64);
            } else {
                String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                TargetHud.mc.textRenderer.drawWithShadow(ms, letter, (float)(hx + (headSize - TargetHud.mc.textRenderer.getWidth(letter)) / 2), (float)(hy + headSize / 2 - 4), -1);
            }
        }

        // name + distance
        TargetHud.mc.textRenderer.drawWithShadow(ms, name, (float)cx, (float)(this.y + pad), -1);
        if (this.showDist.value) {
            TargetHud.mc.textRenderer.drawWithShadow(ms, distStr, (float)(this.x + this.w - pad - TargetHud.mc.textRenderer.getWidth(distStr)), (float)(this.y + pad), DIM);
        }

        // health bar: track, damage trail, current
        int bx = cx;
        int bw = this.x + this.w - pad - cx;
        int by = this.y + pad + 12;
        Render2D.rrect(ms, 3.0f, bx, by, bx + bw, by + 7, TRACK);
        if (trail > ratio) {
            Render2D.rrect(ms, 3.0f, bx, by, bx + Math.max(4, (int)((float)bw * trail)), by + 7, Render2D.ca(RED, 0.55f));
        }
        if (ratio > 0.0f) {
            int fw = Math.max(4, (int)((float)bw * ratio));
            int col = TargetHud.hpColor(ratio);
            Render2D.rrectH(ms, 3.0f, bx, by, bx + fw, by + 7, col, Render2D.lerp(col, -1, 0.25f));
        }

        // numbers
        String hpStr = String.format("%.1f / %.0f", Float.valueOf(hp), Float.valueOf(max));
        int ty = by + 11;
        TargetHud.mc.textRenderer.drawWithShadow(ms, hpStr, (float)bx, (float)ty, -2236963);
        String right = String.format("%d%%", (int)Math.round((double)(Math.max(0.0f, hp) / max * 100.0f)));
        int rx = this.x + this.w - pad - TargetHud.mc.textRenderer.getWidth(right);
        TargetHud.mc.textRenderer.drawWithShadow(ms, right, (float)rx, (float)ty, TargetHud.hpColor(ratio));
        if (abs > 0.0f) {
            String ab = String.format("+%.1f", Float.valueOf(abs));
            int aw = TargetHud.mc.textRenderer.getWidth(ab);
            TargetHud.mc.textRenderer.drawWithShadow(ms, ab, (float)(rx - 6 - aw), (float)ty, GOLD);
        }

        // armor row
        if (armorRow) {
            int ay = this.y + 40;
            int ax = this.x + pad;
            for (ItemStack st : gear) {
                if (!st.isEmpty()) {
                    TargetHud.mc.getItemRenderer().renderGuiItemIcon(st, ax, ay);
                    TargetHud.mc.getItemRenderer().renderGuiItemOverlay(TargetHud.mc.textRenderer, st, ax, ay);
                } else if (preview) {
                    Render2D.rrect(ms, 3.0f, ax, ay, ax + 16, ay + 16, -14803414);
                }
                ax += 19;
            }
        }
    }
}
