package com.hai.sellaxe.modules;

import com.hai.sellaxe.SellAxeAddon;
import com.hai.sellaxe.util.Money;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPayModule extends Module {
    private enum State { IDLE, STOPPING, WAIT_BAL, PAYING, RESTART_WAIT }

    private static final int RETRY_TICKS = 2 * 60 * 20; // th\u1eed l\u1ea1i sau 2 ph\u00fat n\u1ebfu l\u1ed7i

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("N\u00e2ng cao");

    // ---------- Chung ----------
    private final Setting<String> username = sgGeneral.add(new StringSetting.Builder()
        .name("t\u00ean-ng\u01b0\u1eddi-nh\u1eadn")
        .description("T\u00ean ng\u01b0\u1eddi ch\u01a1i nh\u1eadn ti\u1ec1n.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> intervalMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("chu-k\u1ef3-pay-ph\u00fat")
        .description("C\u1ee9 bao nhi\u00eau ph\u00fat th\u00ec pay m\u1ed9t l\u1ea7n.")
        .defaultValue(60)
        .min(1)
        .sliderMax(240)
        .build()
    );

    private final Setting<Double> taxPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("tr\u1eeb-thu\u1ebf-ph\u1ea7n-tr\u0103m")
        .description("S\u1ed1 ph\u1ea7n tr\u0103m tr\u1eeb \u0111i tr\u01b0\u1edbc khi /pay \u0111\u1ec3 ch\u1eeba ch\u1ed7 cho thu\u1ebf (n\u00ean \u0111\u1ec3 3 \u0111\u1ebfn 5).")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> minBalance = sgGeneral.add(new DoubleSetting.Builder()
        .name("s\u1ed1-d\u01b0-t\u1ed1i-thi\u1ec3u")
        .description("S\u1ed1 d\u01b0 ph\u1ea3i l\u1edbn h\u01a1n m\u1ee9c n\u00e0y th\u00ec m\u1edbi pay.")
        .defaultValue(1000)
        .min(0)
        .sliderMax(1000000)
        .build()
    );

    // ---------- N\u00e2ng cao ----------
    private final Setting<Integer> stopDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("t\u1eaft-sellaxe-tr\u01b0\u1edbc-khi-bal-gi\u00e2y")
        .description("T\u1eaft SellAxe bao nhi\u00eau gi\u00e2y r\u1ed3i m\u1edbi g\u00f5 /bal.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> resumeDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("b\u1eadt-l\u1ea1i-sellaxe-sau-pay-gi\u00e2y")
        .description("Sau khi /pay bao nhi\u00eau gi\u00e2y th\u00ec b\u1eadt l\u1ea1i SellAxe.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> failsafeSeconds = sgAdvanced.add(new IntSetting.Builder()
        .name("t\u1ef1-b\u1eadt-l\u1ea1i-sellaxe-sau-gi\u00e2y")
        .description("N\u1ebfu SellAxe b\u1ecb AutoPay t\u1eaft qu\u00e1 s\u1ed1 gi\u00e2y n\u00e0y m\u00e0 ch\u01b0a pay xong th\u00ec t\u1ef1 b\u1eadt l\u1ea1i.")
        .defaultValue(60)
        .min(15)
        .sliderMax(300)
        .build()
    );

    private final Setting<Integer> restartWait = sgAdvanced.add(new IntSetting.Builder()
        .name("ch\u1edd-khi-restart-region-gi\u00e2y")
        .description("Khi th\u1ea5y c\u1ea3nh b\u00e1o restart region gi\u1eefa l\u00fac pay, ch\u1edd bao nhi\u00eau gi\u00e2y r\u1ed3i b\u1eadt l\u1ea1i SellAxe.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<String> balanceRegex = sgAdvanced.add(new StringSetting.Builder()
        .name("regex-\u0111\u1ecdc-s\u1ed1-d\u01b0")
        .description("Regex \u0111\u1ecdc d\u00f2ng chat tr\u1ea3 v\u1ec1 c\u1ee7a /bal. Nh\u00f3m s\u1ed1 1 ph\u1ea3i l\u00e0 s\u1ed1 ti\u1ec1n.")
        .defaultValue("(?i)(?:balance|bal|money|coins)\\D{0,20}\\$?\\s?([\\d,]+(?:\\.\\d+)?[kmbt]?)(?![a-z])")
        .build()
    );

    private final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("g\u1ee1-l\u1ed7i-chat")
        .description("In ra m\u1ecdi d\u00f2ng chat nh\u1eadn \u0111\u01b0\u1ee3c khi \u0111ang ch\u1edd /bal (\u0111\u1ec3 ch\u1ec9nh regex).")
        .defaultValue(false)
        .build()
    );

    private State state = State.IDLE;
    private int timer = 0;
    private int cycleTicks = -1;
    private int stoppedTicks = 0;
    private boolean stoppedByUs = false;
    private String lastRegex = null;
    private Pattern lastPattern = null;

    public AutoPayModule() {
        super(SellAxeAddon.CATEGORY, "AutoPay", "T\u1ef1 /pay to\u00e0n b\u1ed9 ti\u1ec1n (\u0111\u00e3 tr\u1eeb thu\u1ebf) cho m\u1ed9t ng\u01b0\u1eddi ch\u01a1i theo chu k\u1ef3, t\u1ea1m d\u1eebng SellAxe khi pay.");
    }

    @Override
    public void onActivate() {
        if (cycleTicks < 0) cycleTicks = intervalMinutes.get() * 60 * 20;
    }

    @Override
    public void onDeactivate() {
        resumeSellAxe(false);
        state = State.IDLE;
    }

    private Pattern balancePattern() {
        String r = balanceRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex \u0111\u1ecdc s\u1ed1 d\u01b0 kh\u00f4ng h\u1ee3p l\u1ec7.");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private SellAxeModule sellAxe() {
        return Modules.get().get(SellAxeModule.class);
    }

    // skipHome = true khi nh\u00e2n v\u1eadt v\u1eabn \u0111\u1ee9ng \u0111\u00fang ch\u1ed7 (pay xong b\u00ecnh th\u01b0\u1eddng) -> SellAxe kh\u00f4ng c\u1ea7n /home
    private void resumeSellAxe(boolean skipHome) {
        if (!stoppedByUs) return;
        stoppedByUs = false;
        stoppedTicks = 0;
        SellAxeModule sa = sellAxe();
        if (sa != null && !sa.isActive()) {
            if (skipHome) sa.skipHomeNextStart();
            sa.toggle();
        }
    }

    private void abort(int nextCycleTicks, boolean skipHome) {
        resumeSellAxe(skipHome);
        state = State.IDLE;
        cycleTicks = nextCycleTicks;
    }

    private void beginPay() {
        if (username.get().isBlank()) {
            warning("Ch\u01b0a nh\u1eadp t\u00ean ng\u01b0\u1eddi nh\u1eadn cho AutoPay.");
            cycleTicks = intervalMinutes.get() * 60 * 20;
            return;
        }
        SellAxeModule sa = sellAxe();
        if (sa != null && sa.isActive()) {
            sa.toggle();
            stoppedByUs = true;
        }
        stoppedTicks = 0;
        state = State.STOPPING;
        timer = stopDelay.get() * 20;
        info("\u0110\u1ebfn chu k\u1ef3 pay, t\u1ea1m d\u1eebng SellAxe.");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        // V\u00e0o l\u1ea1i server gi\u1eefa ch\u1eebng -> hu\u1ef7 l\u01b0\u1ee3t pay \u0111ang d\u1edf v\u00e0 b\u1eadt l\u1ea1i SellAxe
        if (state != State.IDLE || stoppedByUs) abort(RETRY_TICKS, false);
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        String low = msg.toLowerCase();

        if (low.contains("server are updating") || low.contains("server is updating")) {
            if (state != State.IDLE || stoppedByUs) {
                state = State.RESTART_WAIT;
                timer = restartWait.get() * 20;
            }
            return;
        }

        if (state != State.WAIT_BAL) return;
        if (debug.get()) info("[chat] %s", msg);

        try {
            Matcher m = balancePattern().matcher(msg);
            if (!m.find()) return;

            double balance = Money.parse(m.group(1));
            if (balance < minBalance.get()) {
                info("S\u1ed1 d\u01b0 " + Money.format(balance) + " qu\u00e1 th\u1ea5p, b\u1ecf qua l\u01b0\u1ee3t n\u00e0y.");
                abort(intervalMinutes.get() * 60 * 20, true);
                return;
            }

            long pay = (long) Math.floor(balance * (1.0 - taxPercent.get() / 100.0));
            ChatUtils.sendPlayerMsg("/pay " + username.get() + " " + pay);
            info("Pay " + username.get() + " " + Money.format(pay) + " (s\u1ed1 d\u01b0 " + Money.format(balance) + ").");

            state = State.PAYING;
            timer = resumeDelay.get() * 20;
        } catch (Exception e) {
            warning("Kh\u00f4ng \u0111\u1ecdc \u0111\u01b0\u1ee3c s\u1ed1 d\u01b0: %s", String.valueOf(e.getMessage()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Ch\u1ed1t an to\u00e0n: SellAxe b\u1ecb m\u00ecnh t\u1eaft qu\u00e1 l\u00e2u -> b\u1eadt l\u1ea1i
        if (stoppedByUs && ++stoppedTicks >= failsafeSeconds.get() * 20) {
            warning("Qu\u00e1 th\u1eddi gian ch\u1edd, t\u1ef1 b\u1eadt l\u1ea1i SellAxe.");
            abort(RETRY_TICKS, false);
            return;
        }

        int max = intervalMinutes.get() * 60 * 20;
        if (cycleTicks > max) cycleTicks = max;

        switch (state) {
            case IDLE -> {
                if (--cycleTicks <= 0) beginPay();
            }
            case STOPPING -> {
                if (--timer <= 0) {
                    ChatUtils.sendPlayerMsg("/bal");
                    state = State.WAIT_BAL;
                    timer = 10 * 20;
                }
            }
            case WAIT_BAL -> {
                if (--timer <= 0) {
                    warning("Kh\u00f4ng nh\u1eadn \u0111\u01b0\u1ee3c s\u1ed1 d\u01b0 t\u1eeb /bal, b\u1eadt l\u1ea1i SellAxe.");
                    abort(RETRY_TICKS, true);
                }
            }
            case PAYING -> {
                if (--timer <= 0) abort(max, true);
            }
            case RESTART_WAIT -> {
                if (--timer <= 0) abort(RETRY_TICKS, false);
            }
        }
    }
}
