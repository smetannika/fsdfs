package dev.client.module.impl;

import dev.client.module.BoolSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/** Plays a sound whenever a module is switched on or off. */
public class ClientSound
extends Module {
    private static ClientSound instance;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "clientmod-sound");
        t.setDaemon(true);
        return t;
    });
    private static byte[] enableData;
    private static byte[] disableData;

    private final NumSetting volume = this.add(new NumSetting("Volume %", 60.0, 0.0, 100.0, 5.0));
    private final BoolSetting hudToo = this.add(new BoolSetting("HUD modules", true));

    public ClientSound() {
        super("Client Sound", Module.Category.HUD);
        this.enabled = true;
        instance = this;
    }

    private static byte[] load(String name) {
        try (InputStream in = ClientSound.class.getResourceAsStream("/assets/clientmod/sounds/" + name)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** Called by Module.setEnabled. */
    public static void onToggle(Module m, boolean on) {
        ClientSound cs = instance;
        if (cs == null || !cs.enabled || m == cs) {
            return;
        }
        if (m instanceof dev.client.module.HudModule && !cs.hudToo.value) {
            return;
        }
        final float vol = (float)(cs.volume.value / 100.0);
        if (vol <= 0.0f) {
            return;
        }
        POOL.execute(() -> ClientSound.play(on, vol));
    }

    private static void play(boolean on, float vol) {
        try {
            if (enableData == null) {
                enableData = ClientSound.load("enable.wav");
                disableData = ClientSound.load("disable.wav");
            }
            byte[] data = on ? enableData : disableData;
            if (data == null) {
                return;
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (float)(20.0 * Math.log10(Math.max(vol, 0.0001f)));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            }
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        }
        catch (Throwable t) {
            // no audio device or unsupported format: stay silent
        }
    }
}
