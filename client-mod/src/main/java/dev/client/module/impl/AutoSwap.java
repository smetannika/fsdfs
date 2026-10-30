package dev.client.module.impl;

import dev.client.module.BindSetting;
import dev.client.module.BoolSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import java.util.Locale;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class AutoSwap
extends Module {
    private final BoolSetting totems = this.add(new BoolSetting("Totems", true));
    private final BoolSetting spheres = this.add(new BoolSetting("Spheres", true));
    private final NumSetting delay = this.add(new NumSetting("Delay ms", 80.0, 0.0, 500.0, 5.0));
    // Hotkey swap: the module's bind (or the keys below) swaps your offhand instead of toggling the module.
    private final BoolSetting modeHT = this.add(new BoolSetting("Head <-> Totem", true));
    private final BoolSetting modeTT = this.add(new BoolSetting("Totem <-> Totem", false));
    private final BoolSetting modeHH = this.add(new BoolSetting("Head <-> Head", false));
    private final BindSetting keyHT = this.add(new BindSetting("Key: Head/Totem"));
    private final BindSetting keyTT = this.add(new BindSetting("Key: Totem/Totem"));
    private final BindSetting keyHH = this.add(new BindSetting("Key: Head/Head"));
    private boolean prevHT = true;
    private boolean prevTT;
    private boolean prevHH;
    private boolean downHT;
    private boolean downTT;
    private boolean downHH;
    private Kind lastKind = Kind.NONE;
    private long swapAt = -1L;

    public AutoSwap() {
        super("AutoSwap", Module.Category.COMBAT);
    }

    private static boolean isTotem(ItemStack s) {
        return !s.isEmpty() && s.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private static boolean isHead(ItemStack s) {
        return !s.isEmpty() && s.getItem() == Items.PLAYER_HEAD;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return a.getName().getString().equals(b.getName().getString()) && String.valueOf(a.getTag()).equals(String.valueOf(b.getTag()));
    }

    /** 1 = head/totem, 2 = totem/totem, 3 = head/head */
    private int mode() {
        if (this.modeHH.value) {
            return 3;
        }
        return this.modeTT.value ? 2 : 1;
    }

    /** Pressing the module's bind swaps instead of switching the module off. */
    @Override
    public void onKey() {
        this.swap(this.mode());
    }

    @Override
    public void onAlwaysTick() {
        // the three mode switches behave like radio buttons
        if (this.modeHT.value && !this.prevHT) {
            this.modeTT.value = false;
            this.modeHH.value = false;
        } else if (this.modeTT.value && !this.prevTT) {
            this.modeHT.value = false;
            this.modeHH.value = false;
        } else if (this.modeHH.value && !this.prevHH) {
            this.modeHT.value = false;
            this.modeTT.value = false;
        }
        this.prevHT = this.modeHT.value;
        this.prevTT = this.modeTT.value;
        this.prevHH = this.modeHH.value;

        if (AutoSwap.mc.player == null || AutoSwap.mc.getWindow() == null) {
            return;
        }
        long handle = AutoSwap.mc.getWindow().getHandle();
        boolean dHT = this.keyHT.key > 0 && InputUtil.isKeyPressed(handle, this.keyHT.key);
        boolean dTT = this.keyTT.key > 0 && InputUtil.isKeyPressed(handle, this.keyTT.key);
        boolean dHH = this.keyHH.key > 0 && InputUtil.isKeyPressed(handle, this.keyHH.key);
        if (AutoSwap.mc.currentScreen == null) {
            if (dHT && !this.downHT) {
                this.swap(1);
            } else if (dTT && !this.downTT) {
                this.swap(2);
            } else if (dHH && !this.downHH) {
                this.swap(3);
            }
        }
        this.downHT = dHT;
        this.downTT = dTT;
        this.downHH = dHH;
    }

    private void swap(int mode) {
        if (AutoSwap.mc.player == null || AutoSwap.mc.interactionManager == null || !AutoSwap.mc.player.isAlive() || AutoSwap.mc.currentScreen != null) {
            return;
        }
        ItemStack off = AutoSwap.mc.player.getOffHandStack();
        int slot = -1;
        if (mode == 1) {
            // totem in hand -> head, head in hand -> totem, anything else -> totem (or head if no totem)
            if (AutoSwap.isTotem(off)) {
                slot = this.findOther(true, off, false);
            } else if (AutoSwap.isHead(off)) {
                slot = this.findOther(false, off, false);
            } else {
                slot = this.findOther(false, off, false);
                if (slot < 0) {
                    slot = this.findOther(true, off, false);
                }
            }
        } else if (mode == 2) {
            slot = this.findOther(false, off, AutoSwap.isTotem(off));
        } else {
            slot = this.findOther(true, off, AutoSwap.isHead(off));
        }
        if (slot >= 0) {
            AutoSwap.mc.interactionManager.clickSlot(AutoSwap.mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, (PlayerEntity)AutoSwap.mc.player);
        }
    }

    /**
     * Finds a slot with a head (head = true) or a totem. With differ = true it prefers a stack that is not
     * identical to the offhand one (so two different heads / totems actually trade places).
     */
    private int findOther(boolean head, ItemStack off, boolean differ) {
        int fallback = -1;
        for (int i = 0; i < 36; ++i) {
            ItemStack s = (ItemStack)AutoSwap.mc.player.inventory.main.get(i);
            if (!(head ? AutoSwap.isHead(s) : AutoSwap.isTotem(s))) continue;
            int screenSlot = i < 9 ? 36 + i : i;
            if (!differ || !AutoSwap.same(s, off)) {
                return screenSlot;
            }
            if (fallback < 0) {
                fallback = screenSlot;
            }
        }
        return fallback;
    }

    @Override
    public void onDisable() {
        this.lastKind = Kind.NONE;
        this.swapAt = -1L;
    }

    private static boolean isSphere(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        String n = s.getName().getString().toLowerCase(Locale.ROOT);
        return n.contains("сфер") || n.contains("sphere");
    }

    private Kind kindOf(ItemStack s) {
        if (s.isEmpty()) {
            return Kind.NONE;
        }
        if (this.totems.value && s.getItem() == Items.TOTEM_OF_UNDYING) {
            return Kind.TOTEM;
        }
        if (this.spheres.value && AutoSwap.isSphere(s)) {
            return Kind.SPHERE;
        }
        return Kind.NONE;
    }

    private boolean matches(ItemStack s, Kind k) {
        if (s.isEmpty()) {
            return false;
        }
        if (k == Kind.TOTEM) {
            return s.getItem() == Items.TOTEM_OF_UNDYING;
        }
        if (k == Kind.SPHERE) {
            return AutoSwap.isSphere(s);
        }
        return false;
    }

    @Override
    public void onTick() {
        if (AutoSwap.mc.player == null || AutoSwap.mc.interactionManager == null) {
            return;
        }
        if (!AutoSwap.mc.player.isAlive()) {
            this.lastKind = Kind.NONE;
            this.swapAt = -1L;
            return;
        }
        if (AutoSwap.mc.currentScreen instanceof HandledScreen) {
            return;
        }
        Kind cur = this.kindOf(AutoSwap.mc.player.getOffHandStack());
        if (cur != Kind.NONE) {
            this.lastKind = cur;
            this.swapAt = -1L;
            return;
        }
        if (!AutoSwap.mc.player.getOffHandStack().isEmpty() || this.lastKind == Kind.NONE) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.swapAt < 0L) {
            this.swapAt = now + (long)this.delay.value;
        }
        if (now < this.swapAt) {
            return;
        }
        int slot = this.findSlot(this.lastKind);
        if (slot >= 0) {
            AutoSwap.mc.interactionManager.clickSlot(AutoSwap.mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, (PlayerEntity)AutoSwap.mc.player);
        }
        this.lastKind = Kind.NONE;
        this.swapAt = -1L;
    }

    private int findSlot(Kind k) {
        for (int i = 0; i < 36; ++i) {
            ItemStack s = (ItemStack)AutoSwap.mc.player.inventory.main.get(i);
            if (!this.matches(s, k)) continue;
            return i < 9 ? 36 + i : i;
        }
        return -1;
    }

    private static enum Kind {
        NONE,
        TOTEM,
        SPHERE;

    }
}

