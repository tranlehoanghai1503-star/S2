package com.hai.sellaxe.modules;

import com.hai.sellaxe.SellAxeAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Pattern;

public class SellAxeModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDeath = settings.createGroup("Khi ch\u1ebft");
    private final SettingGroup sgRestart = settings.createGroup("Khi restart region");
    private final SettingGroup sgIdle = settings.createGroup("Ch\u1ed1ng \u0111\u1ee9ng y\u00ean");

    // ---------- Chung ----------
    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("gi\u1eef-sneak")
        .description("Gi\u1eef ph\u00edm sneak \u1edf b\u01b0\u1edbc \u0111\u1ea7u ti\u00ean.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stepDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tr\u1ec5-m\u1ed7i-b\u01b0\u1edbc-tick")
        .description("Kho\u1ea3ng c\u00e1ch gi\u1eefa c\u00e1c b\u01b0\u1edbc: nh\u00ecn l\u00ean, gi\u1eef chu\u1ed9t ph\u1ea3i, gi\u1eef chu\u1ed9t tr\u00e1i (10 tick = 0,5 gi\u00e2y).")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> reconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("ch\u1edd-sau-khi-v\u00e0o-l\u1ea1i-gi\u00e2y")
        .description("Sau khi v\u00e0o l\u1ea1i server (ho\u1eb7c h\u1ebft restart region) t\u1eaft bao nhi\u00eau gi\u00e2y r\u1ed3i m\u1edbi ch\u1ea1y l\u1ea1i.")
        .defaultValue(5)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> homeOnStart = sgGeneral.add(new BoolSetting.Builder()
        .name("t\u1ef1-home-khi-b\u1eadt")
        .description("M\u1ed7i l\u1ea7n b\u1eadt SellAxe (ho\u1eb7c ch\u1ea1y l\u1ea1i sau khi v\u00e0o l\u1ea1i server) s\u1ebd t\u1ef1 g\u00f5 /home tr\u01b0\u1edbc khi b\u1eaft \u0111\u1ea7u.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> homeName = sgGeneral.add(new StringSetting.Builder()
        .name("t\u00ean-home")
        .description("T\u00ean home d\u00f9ng cho m\u1ecdi l\u1ec7nh /home c\u1ee7a SellAxe (v\u00ed d\u1ee5: 1).")
        .defaultValue("1")
        .build()
    );

    private final Setting<Integer> homeWait = sgGeneral.add(new IntSetting.Builder()
        .name("ch\u1edd-sau-home-gi\u00e2y")
        .description("Sau khi g\u00f5 /home, ch\u1edd bao nhi\u00eau gi\u00e2y cho d\u1ecbch chuy\u1ec3n xong r\u1ed3i m\u1edbi ch\u1ea1y.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> workInFreecam = sgGeneral.add(new BoolSetting.Builder()
        .name("ch\u1ea1y-khi-b\u1eadt-freecam")
        .description("Khi b\u1eadt Freecam c\u1ee7a Meteor, SellAxe v\u1eabn ch\u1eb7t/\u0103n theo h\u01b0\u1edbng nh\u00ecn c\u1ee7a nh\u00e2n v\u1eadt (kh\u00f4ng theo camera).")
        .defaultValue(true)
        .build()
    );

    // ---------- Khi ch\u1ebft ----------
    private final Setting<Boolean> deathHandling = sgDeath.add(new BoolSetting.Builder()
        .name("t\u1ef1-x\u1eed-l\u00fd-khi-ch\u1ebft")
        .description("Khi nh\u00e2n v\u1eadt ch\u1ebft (c\u1ea7n b\u1eadt AutoRespawn c\u1ee7a Meteor): t\u1ef1 /home r\u1ed3i t\u1eaft v\u00e0 b\u1eadt l\u1ea1i SellAxe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> deathHomeDelay = sgDeath.add(new IntSetting.Builder()
        .name("ch\u1edd-home-sau-khi-ch\u1ebft-gi\u00e2y")
        .description("Sau khi ch\u1ebft bao nhi\u00eau gi\u00e2y th\u00ec g\u00f5 /home (t\u00ednh t\u1eeb l\u00fac ch\u1ebft).")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> deathRestartDelay = sgDeath.add(new IntSetting.Builder()
        .name("b\u1eadt-l\u1ea1i-sau-home-gi\u00e2y")
        .description("Sau khi g\u00f5 /home, SellAxe t\u1eaft v\u00e0 b\u1eadt l\u1ea1i sau bao nhi\u00eau gi\u00e2y.")
        .defaultValue(3)
        .min(1)
        .sliderMax(30)
        .build()
    );

    // ---------- Khi restart region ----------
    private final Setting<String> restartBarRegex = sgRestart.add(new StringSetting.Builder()
        .name("regex-d\u00f2ng-ch\u1eef-hotbar")
        .description("Regex nh\u1eadn d\u00f2ng ch\u1eef tr\u00ean thanh hotbar khi server \u0111ang restart ho\u1eb7c x\u1ebfp h\u00e0ng.")
        .defaultValue("(?i)(restart|updat|queue|position|slot|region|server)")
        .build()
    );

    private final Setting<Integer> barGoneSeconds = sgRestart.add(new IntSetting.Builder()
        .name("hotbar-m\u1ea5t-ch\u1eef-gi\u00e2y")
        .description("D\u00f2ng ch\u1eef tr\u00ean hotbar bi\u1ebfn m\u1ea5t li\u00ean t\u1ee5c bao nhi\u00eau gi\u00e2y th\u00ec coi l\u00e0 restart xong.")
        .defaultValue(3)
        .min(1)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> restartFallback = sgRestart.add(new IntSetting.Builder()
        .name("ch\u1edd-d\u1ef1-ph\u00f2ng-gi\u00e2y")
        .description("N\u1ebfu kh\u00f4ng th\u1ea5y d\u00f2ng ch\u1eef n\u00e0o tr\u00ean hotbar th\u00ec ch\u1edd bao nhi\u00eau gi\u00e2y r\u1ed3i ch\u1ea1y l\u1ea1i.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<Integer> restartMaxWait = sgRestart.add(new IntSetting.Builder()
        .name("ch\u1edd-t\u1ed1i-\u0111a-gi\u00e2y")
        .description("Ch\u1edd t\u1ed1i \u0111a bao nhi\u00eau gi\u00e2y (ph\u00f2ng khi d\u00f2ng ch\u1eef kh\u00f4ng bao gi\u1edd m\u1ea5t).")
        .defaultValue(600)
        .min(30)
        .sliderMax(1800)
        .build()
    );

    private final Setting<Boolean> debug = sgRestart.add(new BoolSetting.Builder()
        .name("g\u1ee1-l\u1ed7i-hotbar")
        .description("In ra c\u00e1c d\u00f2ng ch\u1eef tr\u00ean hotbar nh\u1eadn \u0111\u01b0\u1ee3c trong l\u00fac restart (\u0111\u1ec3 ch\u1ec9nh regex).")
        .defaultValue(false)
        .build()
    );

    // ---------- Ch\u1ed1ng \u0111\u1ee9ng y\u00ean ----------
    private final Setting<Boolean> autoHome = sgIdle.add(new BoolSetting.Builder()
        .name("t\u1ef1-home-khi-\u0111\u1ee9ng-y\u00ean")
        .description("\u0110\u1ee9ng y\u00ean qu\u00e1 l\u00e2u th\u00ec t\u1ef1 /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> idleSeconds = sgIdle.add(new IntSetting.Builder()
        .name("\u0111\u1ee9ng-y\u00ean-bao-l\u00e2u-gi\u00e2y")
        .description("\u0110\u1ee9ng y\u00ean bao nhi\u00eau gi\u00e2y th\u00ec /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> noChestHome = sgIdle.add(new BoolSetting.Builder()
        .name("t\u1ef1-home-khi-kh\u00f4ng-ch\u1eb7t-r\u01b0\u01a1ng")
        .description("Qu\u00e1 l\u00e2u kh\u00f4ng ch\u1eb7t \u0111\u01b0\u1ee3c r\u01b0\u01a1ng th\u00ec t\u1ef1 /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> noChestSeconds = sgIdle.add(new IntSetting.Builder()
        .name("kh\u00f4ng-ch\u1eb7t-r\u01b0\u01a1ng-bao-l\u00e2u-gi\u00e2y")
        .description("Bao nhi\u00eau gi\u00e2y kh\u00f4ng ch\u1eb7t r\u01b0\u01a1ng th\u00ec /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> anyBlock = sgIdle.add(new BoolSetting.Builder()
        .name("t\u00ednh-c\u1ea3-block-kh\u00e1c")
        .description("T\u00ednh c\u1ea3 block kh\u00e1c (barrel, shulker...) l\u00e0 \u0111ang ch\u1eb7t, kh\u00f4ng ch\u1ec9 r\u01b0\u01a1ng.")
        .defaultValue(false)
        .build()
    );

    // 0 = ch\u1edd (ho\u1eb7c ch\u1edd /home), 1 = kho\u00e1 nh\u00ecn l\u00ean (+sneak), 2 = gi\u1eef chu\u1ed9t ph\u1ea3i, 3 = gi\u1eef c\u1ea3 chu\u1ed9t tr\u00e1i
    private int stage = 0;
    private int timer = 0;
    private boolean pendingHome = false;
    private boolean skipHomeOnce = false;

    private volatile int tick = 0;
    private volatile boolean restartMode = false;
    private volatile boolean barSeen = false;
    private volatile int lastBarTick = 0;
    private int restartTicks = 0;

    // X\u1eed l\u00fd khi ch\u1ebft
    private boolean deathMode = false;
    private boolean wasDead = false;
    private boolean homeSentAfterDeath = false;
    private int deathTicks = 0;
    private int postHomeTicks = 0;

    private Object lastWorld = null;
    private Vec3d idleAnchor = null;
    private int idleTicks = 0;
    private int noChestTicks = 0;
    private boolean breakingChest = false;

    // \u0110\u1ed3ng b\u1ed9 camera Freecam v\u1edbi nh\u00e2n v\u1eadt trong l\u00fac game x\u1eed l\u00fd tick
    private Freecam syncedFreecam = null;
    private double savedX, savedY, savedZ;
    private float savedYaw, savedPitch;

    private String lastRegex = null;
    private Pattern lastPattern = null;

    public SellAxeModule() {
        super(SellAxeAddon.CATEGORY, "SellAxe", "SellAxe by H\u1ea3i Tr\u1ea7n");
    }

    /** AutoPay g\u1ecdi tr\u01b0\u1edbc khi b\u1eadt l\u1ea1i SellAxe \u0111\u1ec3 kh\u00f4ng /home th\u1eeba (\u0111ang \u0111\u1ee9ng \u0111\u00fang ch\u1ed7 r\u1ed3i). */
    public void skipHomeNextStart() {
        skipHomeOnce = true;
    }

    @Override
    public void onActivate() {
        lastWorld = mc.world;
        restartMode = false;
        deathMode = false;
        wasDead = false;

        boolean justJoined = mc.player == null || mc.player.age < 40;
        boolean goHome = !skipHomeOnce;
        skipHomeOnce = false;
        start(justJoined ? reconnectDelay.get() * 20 : 0, goHome);
    }

    @Override
    public void onDeactivate() {
        restoreFreecam();
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
    }

    private void start(int delayTicks, boolean goHome) {
        releaseKeys();
        stage = 0;
        timer = delayTicks;
        pendingHome = goHome && homeOnStart.get();
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
    }

    private void releaseKeys() {
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    private void sendHomeCommand() {
        String name = homeName.get().trim();
        ChatUtils.sendPlayerMsg(name.isEmpty() ? "/home" : "/home " + name);
    }

    private Pattern barPattern() {
        String r = restartBarRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex d\u00f2ng ch\u1eef hotbar kh\u00f4ng h\u1ee3p l\u1ec7.");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private void enterRestart() {
        if (restartMode || deathMode) return;
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartTicks = 0;
        barSeen = false;
        lastBarTick = tick;
        restartMode = true;
        info("Ph\u00e1t hi\u1ec7n restart region, t\u1ea1m d\u1eebng SellAxe.");
    }

    private void enterDeath() {
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartMode = false;
        deathMode = true;
        deathTicks = 0;
        postHomeTicks = 0;
        homeSentAfterDeath = false;
        info("Nh\u00e2n v\u1eadt \u0111\u00e3 ch\u1ebft, t\u1ea1m d\u1eebng SellAxe.");
    }

    // ---------- S\u1ef1 ki\u1ec7n ----------

    // V\u00e0o l\u1ea1i server: t\u1eaft (nh\u1ea3 ph\u00edm), ch\u1edd r\u1ed3i /home v\u00e0 ch\u1ea1y l\u1ea1i
    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        restartMode = false;
        deathMode = false;
        wasDead = false;
        lastWorld = mc.world;
        start(reconnectDelay.get() * 20, true);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
        lastWorld = null;
    }

    // D\u00f2ng c\u1ea3nh b\u00e1o restart region trong chat
    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
        if (msg.contains("server are updating") || msg.contains("server is updating")) {
            enterRestart();
        }
    }

    // D\u00f2ng ch\u1eef tr\u00ean thanh hotbar (action bar)
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!restartMode) return;

        Text text = null;
        if (event.packet instanceof OverlayMessageS2CPacket p) text = p.text();
        else if (event.packet instanceof GameMessageS2CPacket p && p.overlay()) text = p.content();
        if (text == null) return;

        String s = text.getString();
        if (debug.get()) mc.execute(() -> info("[hotbar] %s", s));

        if (barPattern().matcher(s).find()) {
            barSeen = true;
            lastBarTick = tick;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        if (mc.player == null || mc.world == null) return;

        // ----- Ch\u1ebft: /home sau 5 gi\u00e2y r\u1ed3i t\u1eaft/b\u1eadt l\u1ea1i SellAxe -----
        boolean dead = mc.player.isDead() || mc.currentScreen instanceof DeathScreen;
        if (deathHandling.get()) {
            if (dead && !wasDead) enterDeath();
            wasDead = dead;

            if (deathMode) {
                handleDeath(dead);
                return;
            }
        } else {
            wasDead = dead;
            if (deathMode) {
                deathMode = false;
                start(0, false);
            }
        }

        // \u0110\u1ed5i world m\u00e0 kh\u00f4ng c\u00f3 s\u1ef1 ki\u1ec7n join (b\u1ecb chuy\u1ec3n region) -> t\u1eaft r\u1ed3i ch\u1ea1y l\u1ea1i
        if (mc.world != lastWorld) {
            boolean first = lastWorld == null;
            lastWorld = mc.world;
            idleAnchor = null;
            idleTicks = 0;
            if (!first && !restartMode) start(reconnectDelay.get() * 20, true);
        }

        // \u0110ang ch\u1edd restart region: \u0111\u1ee3i d\u00f2ng ch\u1eef tr\u00ean hotbar bi\u1ebfn m\u1ea5t
        if (restartMode) {
            restartTicks++;
            boolean barGone = barSeen && (tick - lastBarTick) >= barGoneSeconds.get() * 20;
            boolean noBar = !barSeen && restartTicks >= restartFallback.get() * 20;
            boolean tooLong = restartTicks >= restartMaxWait.get() * 20;
            if (barGone || noBar || tooLong) {
                restartMode = false;
                info("Restart xong, SellAxe s\u1ebd ch\u1ea1y l\u1ea1i.");
                start(reconnectDelay.get() * 20, true);
            }
            return;
        }

        if (stage >= 1) mc.player.setPitch(-90f);

        if (timer > 0) {
            timer--;
            return;
        }

        switch (stage) {
            case 0 -> {
                if (pendingHome) {
                    pendingHome = false;
                    sendHomeCommand();
                    info("T\u1ef1 /home tr\u01b0\u1edbc khi ch\u1ea1y.");
                    timer = homeWait.get() * 20;
                } else {
                    mc.player.setPitch(-90f);
                    if (sneak.get()) mc.options.sneakKey.setPressed(true);
                    stage = 1;
                    timer = stepDelay.get();
                }
            }
            case 1 -> {
                mc.options.useKey.setPressed(true);
                stage = 2;
                timer = stepDelay.get();
            }
            case 2 -> {
                mc.options.attackKey.setPressed(true);
                stage = 3;
            }
            case 3 -> {
                if (sneak.get()) mc.options.sneakKey.setPressed(true);
                mc.options.useKey.setPressed(true);
                mc.options.attackKey.setPressed(true);
                checkIdle();
                checkChest();
            }
        }
    }

    // Sau khi ch\u1ebft: \u0111\u1ee7 th\u1eddi gian + \u0111\u00e3 h\u1ed3i sinh -> /home; sau \u0111\u00f3 ch\u1edd r\u1ed3i ch\u1ea1y l\u1ea1i
    private void handleDeath(boolean dead) {
        deathTicks++;
        if (dead) return; // ch\u01b0a h\u1ed3i sinh (\u0111\u1ee3i AutoRespawn)

        if (!homeSentAfterDeath) {
            if (deathTicks >= deathHomeDelay.get() * 20) {
                sendHomeCommand();
                homeSentAfterDeath = true;
                postHomeTicks = 0;
                info("\u0110\u00e3 /home sau khi ch\u1ebft, SellAxe s\u1ebd b\u1eadt l\u1ea1i sau " + deathRestartDelay.get() + " gi\u00e2y.");
            }
            return;
        }

        if (++postHomeTicks >= deathRestartDelay.get() * 20) {
            deathMode = false;
            start(0, false); // \u0111\u00e3 /home r\u1ed3i n\u00ean kh\u00f4ng /home l\u1ea7n n\u1eefa
        }
    }

    // Freecam c\u1ee7a Meteor l\u00e0m game ng\u1eafm/ch\u1eb7t theo camera ch\u1ee9 kh\u00f4ng theo nh\u00e2n v\u1eadt.
    // \u0110\u1ea7u m\u1ed7i tick: t\u1ea1m \u0111\u01b0a camera Freecam v\u1ec1 m\u1eaft + h\u01b0\u1edbng nh\u00ecn c\u1ee7a nh\u00e2n v\u1eadt \u0111\u1ec3 game
    // t\u00ednh t\u00e2m ng\u1eafm \u0111\u00fang; cu\u1ed1i tick: tr\u1ea3 camera v\u1ec1 ch\u1ed7 c\u0169 cho b\u1ea1n ti\u1ebfp t\u1ee5c xem.
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickSync(TickEvent.Pre event) {
        restoreFreecam();

        if (!workInFreecam.get() || mc.player == null || mc.world == null) return;
        if (stage < 1 || restartMode || deathMode) return;

        Freecam fc = Modules.get().get(Freecam.class);
        if (fc == null || !fc.isActive()) return;

        savedX = fc.pos.x;
        savedY = fc.pos.y;
        savedZ = fc.pos.z;
        savedYaw = fc.yaw;
        savedPitch = fc.pitch;

        Vec3d eye = mc.player.getEyePos();
        fc.pos.set(eye.x, eye.y, eye.z);
        fc.yaw = mc.player.getYaw();
        fc.pitch = mc.player.getPitch();
        syncedFreecam = fc;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickRestore(TickEvent.Post event) {
        restoreFreecam();
    }

    private void restoreFreecam() {
        if (syncedFreecam == null) return;
        syncedFreecam.pos.set(savedX, savedY, savedZ);
        syncedFreecam.yaw = savedYaw;
        syncedFreecam.pitch = savedPitch;
        syncedFreecam = null;
    }

    // \u0110\u1ee9ng y\u00ean qu\u00e1 l\u00e2u (b\u1ecb k\u1eb9t cu\u1ed1i \u0111\u01b0\u1eddng n\u01b0\u1edbc) -> /home
    private void checkIdle() {
        if (!autoHome.get()) {
            idleAnchor = null;
            idleTicks = 0;
            return;
        }

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (idleAnchor == null || pos.squaredDistanceTo(idleAnchor) > 1.0) {
            idleAnchor = pos;
            idleTicks = 0;
            return;
        }

        if (++idleTicks >= idleSeconds.get() * 20) {
            sendHome("\u0110\u1ee9ng y\u00ean qu\u00e1 l\u00e2u, t\u1ef1 /home.");
        }
    }

    // Ki\u1ec3m tra c\u00f3 \u0111ang ch\u1eb7t r\u01b0\u01a1ng kh\u00f4ng (\u0111ang \u0111\u1eadp block v\u00e0 tia ng\u1eafm c\u1ee7a nh\u00e2n v\u1eadt tr\u1ecf v\u00e0o r\u01b0\u01a1ng)
    private boolean isBreakingChest() {
        if (mc.interactionManager == null || mc.world == null) return false;
        if (!mc.interactionManager.isBreakingBlock()) return false;
        HitResult target = mc.player.raycast(4.5, 1.0F, false);
        if (!(target instanceof BlockHitResult hit)) return false;
        if (hit.getType() != HitResult.Type.BLOCK) return false;

        var block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
        return anyBlock.get() || block instanceof ChestBlock;
    }

    // Qu\u00e1 l\u00e2u kh\u00f4ng ch\u1eb7t r\u01b0\u01a1ng -> /home
    private void checkChest() {
        breakingChest = isBreakingChest();

        if (!noChestHome.get() || breakingChest) {
            noChestTicks = 0;
            return;
        }

        if (++noChestTicks >= noChestSeconds.get() * 20) {
            sendHome("Kh\u00f4ng ch\u1eb7t \u0111\u01b0\u1ee3c r\u01b0\u01a1ng trong " + noChestSeconds.get() + " gi\u00e2y, t\u1ef1 /home.");
        }
    }

    private void sendHome(String reason) {
        sendHomeCommand();
        info("%s", reason);
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
    }

    // Hi\u1ec7n tr\u1ea1ng th\u00e1i ngay trong danh s\u00e1ch module c\u1ee7a Meteor
    @Override
    public String getInfoString() {
        if (deathMode) return "V\u1eeba ch\u1ebft";
        if (restartMode) return "Restart region";
        if (stage < 3) return "\u0110ang kh\u1edfi \u0111\u1ed9ng";
        return breakingChest ? "\u0110ang ch\u1eb7t r\u01b0\u01a1ng" : "Kh\u00f4ng ch\u1eb7t";
    }
}
