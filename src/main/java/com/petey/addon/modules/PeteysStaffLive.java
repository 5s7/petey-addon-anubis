package com.petey.addon.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.petey.addon.PeteyAddon;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * PeteysStaffLive — Notifies you when DonutSMP staff go live on YouTube or TikTok.
 * Made by Petey.
 *
 * Intermediary → yarn class map used here:
 *   class_310           → MinecraftClient
 *   field_1697          → MinecraftClient.runDirectory
 *   field_1687          → MinecraftClient.world
 *   method_1551()       → MinecraftClient.getInstance()
 *   method_1566()       → MinecraftClient.getToastManager()
 *   method_1999(toast)  → ToastManager.add(Toast)
 *   class_368           → Toast (interface; ChallengeToast implements it)
 *   class_1799          → ItemStack
 *   class_1802          → Items
 *   class_1935          → Item (used as Items.PAPER)
 *   class_2558          → ClickEvent
 *   class_2558$class_10608 → ClickEvent.OpenUrl (record/inner)
 *   class_2561          → Text
 *   method_43470(str)   → Text.literal(String)
 *   class_2583          → Style
 *   method_10958(event) → Style.withClickEvent(ClickEvent)
 *   method_30938(bool)  → Style.withUnderline(Boolean)
 *   method_27694(fn)    → MutableText.styled(UnaryOperator<Style>)
 *   method_26853(t)     → MutableText.append(Text)
 */
public final class PeteysStaffLive extends ClientModule {

    private static final Logger LOG = LoggerFactory.getLogger("Petey Addon");

    // Fake Chrome UA to avoid bot-detection on YouTube / TikTok pages
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    /** Hardcoded list of DonutSMP staff to monitor. */
    private static final List<Channel> CHANNELS = List.of(
            new Channel(true,  "wizoxlol"),
            new Channel(true,  "pastagamer08"),
            new Channel(false, "pastagamer08"),
            new Channel(false, "theauzzi"),
            new Channel(false, "u_vv7_"),
            new Channel(true,  "DrDonutt")
    );

    /** Drop a file with this name in .minecraft/ to trigger a test notification. */
    private static final String TEST_FILE = "petey-staff-live.test";

    /** Format for test-trigger file content: {@code yt:@handle} or {@code tt:handle}. */
    private static final Pattern SHORT =
            Pattern.compile("^(yt|tt):@?([A-Za-z0-9._-]+)$", Pattern.CASE_INSENSITIVE);

    // YouTube scrape markers
    private static final String CANONICAL = "<link rel=\"canonical\" href=\"";
    private static final String LIVE_NOW  = "\"isLiveNow\":true";

    // ── Settings ─────────────────────────────────────────────────────────────
    private final NumberSetting interval = (NumberSetting) this.addSetting(
            "check-every",
            (Setting) new NumberSetting("Check Every (s)", 60f, 15f, 600f).step(5f));

    // ── Runtime state ────────────────────────────────────────────────────────
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** channel key → whether they were last seen live */
    private final Map<String, Boolean> live    = new ConcurrentHashMap<>();
    private volatile Thread            worker  = null;

    // ── Constructor ──────────────────────────────────────────────────────────
    public PeteysStaffLive() {
        super("peteys-staff-live", "Petey's Staff Live",
                "Notifies you when DonutSMP staff go live on YouTube or TikTok. Made by Petey.",
                PeteyAddon.CATEGORY);
        this.usesNotifications();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onEnable() {
        this.live.clear();
        Thread t = new Thread(this::run, "Petey's Staff Live");
        t.setDaemon(true);
        this.worker = t;
        t.start();
    }

    @Override
    protected void onDisable() {
        Thread t = this.worker;
        this.worker = null;
        if (t != null) t.interrupt();
    }

    // ── Background worker ────────────────────────────────────────────────────
    private void run() {
        long next = 0L;
        while (this.worker == Thread.currentThread()) {
            if (System.currentTimeMillis() >= next) {
                for (Channel c : CHANNELS) {
                    if (this.worker != Thread.currentThread()) return;
                    this.check(c, false);
                }
                next = System.currentTimeMillis() + (long) (this.interval.get() * 1000f);
            }
            this.testTrigger();
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Reads a test-trigger file from .minecraft/ and fires a fake notification. */
    private void testTrigger() {
        Path f = MinecraftClient.getInstance().runDirectory.toPath().resolve(TEST_FILE);
        if (!Files.exists(f, (LinkOption[]) new LinkOption[0])) return;
        try {
            String text = Files.readString(f).trim();
            Files.deleteIfExists(f);
            Matcher m = SHORT.matcher(text);
            if (m.find()) {
                this.check(new Channel(m.group(1).equalsIgnoreCase("yt"), m.group(2)), true);
            }
        } catch (IOException e) {
            LOG.debug("[Petey's Staff Live] test trigger failed: {}", e.toString());
        }
    }

    // ── Live-status check ────────────────────────────────────────────────────
    private void check(Channel c, boolean test) {
        Stream s;
        try {
            s = c.youtube() ? this.youtube(c.handle()) : this.tiktok(c.handle());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            LOG.debug("[Petey's Staff Live] check of {} failed: {}", c, e.toString());
            return;
        }
        if (s == null) return;

        if (test) {
            if (s.live()) {
                this.notifyLive(c, s);
            } else {
                ModuleChat.send(this, ModuleChat.body()
                        .text("Test: ").name(c.handle()).text(" isn't live right now."));
            }
            return;
        }

        Boolean was = this.live.put(c.key(), s.live());
        if (s.live() && !Boolean.TRUE.equals(was)) {
            this.notifyLive(c, s);
        }
    }

    // ── YouTube scrape ────────────────────────────────────────────────────────
    private Stream youtube(String handle) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("https://www.youtube.com/@" + handle + "/live"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip")
                .header("Cookie", "SOCS=CAI; CONSENT=YES+")
                .build();

        HttpResponse<InputStream> res = this.http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream raw = res.body()) {
            if (res.statusCode() != 200) return null;

            boolean gzip = res.headers().firstValue("Content-Encoding")
                    .map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
            InputStreamReader in = new InputStreamReader(
                    gzip ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8);

            char[]        buf    = new char[16384];
            StringBuilder window = new StringBuilder();
            String        watch  = null;
            int           n;

            while ((n = in.read(buf)) > 0) {
                window.append(buf, 0, n);

                if (watch == null) {
                    int at  = window.indexOf(CANONICAL);
                    int end = at < 0 ? -1 : window.indexOf("\"", at + CANONICAL.length());
                    if (end >= 0) {
                        String href = window.substring(at + CANONICAL.length(), end);
                        if (!href.contains("/watch?v=")) {
                            return new Stream(false, null, null, null);
                        }
                        watch = href;
                    }
                }

                if (watch != null && window.indexOf(LIVE_NOW) >= 0) {
                    return new Stream(true, null, watch, null);
                }

                // Keep only a small tail to avoid unbounded memory use
                if (window.length() > 512) {
                    window.delete(0, window.length() - 512);
                }
            }
            return new Stream(false, null, null, null);
        }
    }

    // ── TikTok API check ─────────────────────────────────────────────────────
    private Stream tiktok(String user) throws Exception {
        String url = "https://www.tiktok.com/api-live/user/room/"
                + "?aid=1988&app_language=en&app_name=tiktok_web&sourceType=54&uniqueId="
                + URLEncoder.encode(user, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> res = this.http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;

        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonObject data = obj(root, "data");
        JsonObject room = obj(data, "liveRoom");
        if (room == null || !room.has("status")) return null;

        boolean on    = room.get("status").getAsInt() == 2;
        JsonObject u  = obj(data, "user");
        String nick   = u != null && u.has("nickname") ? u.get("nickname").getAsString() : null;
        String title  = room.has("title") ? room.get("title").getAsString() : null;

        return new Stream(on, title, "https://www.tiktok.com/@" + user + "/live", nick);
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private void notifyLive(Channel c, Stream s) {
        String url      = s.link() != null ? s.link() : "https://www.youtube.com/@" + c.handle() + "/live";
        String who      = (s.nick() == null || s.nick().equalsIgnoreCase(c.handle()))
                          ? c.handle()
                          : c.handle() + " (" + s.nick() + ")";
        String platform = c.youtube() ? "YouTube" : "TikTok";

        ModuleChat.Body body = ModuleChat.body()
                .name(who)
                .text(" is ")
                .danger("LIVE")
                .text(" on " + platform);

        if (s.title() != null && !s.title().isBlank()) {
            body.text(": " + s.title());
        }

        // Clickable [watch] link
        MutableText watchLink = Text.literal("[watch]")
                .styled(st -> st
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withUnderline(true));
        body.text(" ").append(watchLink);

        ModuleChat.send(this, body);

        // Toast notification
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.getToastManager().add(
                new ChallengeToast(
                        new ItemStack(Items.PAPER),
                        Text.literal(who + " is LIVE"),
                        Text.literal("on " + platform),
                        true
                )
        ));
    }

    // ── Records ───────────────────────────────────────────────────────────────
    record Channel(boolean youtube, String handle) {
        String key() {
            return (youtube ? "yt:" : "tt:") + handle.toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return key();
        }
    }

    private record Stream(boolean live, String title, String link, String nick) {}
}
