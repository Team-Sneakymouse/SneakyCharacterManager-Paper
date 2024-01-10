package net.sneakycharactermanager.paper.handlers.skins;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.destroystokyo.paper.profile.ProfileProperty;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.util.SkinUtil;

public class SkinData {

    /*
    * Example code on how to create custom skin data.
    * Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s)->{
            SkinData data = new SkinData(args[0], false);
            new BukkitRunnable() {
                @Override
                public void run() {
                    PlayerProfile profile = player.getPlayerProfile();
                    profile.removeProperty("textures");
                    profile.setProperty(data.getTextureProperty());
                    player.setPlayerProfile(profile);

                    for(Player target : Bukkit.getOnlinePlayers()) {
                        target.hidePlayer(SneakyCharacterManager.getInstance(), player);
                        target.showPlayer(SneakyCharacterManager.getInstance(), player);
                    }
                    player.sendMessage("Changed skin!");
                }
            }.runTask(SneakyCharacterManager.getInstance());
        });

        player.sendMessage("Attempting to change skin!");
    * */


    private final String url;
    private final boolean isSlim;
    private final int priority;
    private final Player player;
    
    private final SkullMeta skullMeta;
    private final ItemStack characterHead;
    private final Inventory inventory;
    private final int index;

    private String texture;
    private String signature;
    private boolean isValid = false;
    private boolean cancelled = false;
    private int attempts = 0;

    private static ConcurrentMap<String, SkinData> skinDataMap = new ConcurrentHashMap<>();

    private static final String MINESKIN_API_URL = "https://api.mineskin.org/generate/url";

    /**
     * Load custom skill data from a normal web URL.
     * Things to note:
     * RUN THIS ASYNC.. It will freeze the server while it makes an HTTP request and so
     * to avoid over freezing the server with too many character changes using
     * "Bukkit.getAsyncScheduler().runNow(...)" is a good idea.
     * Example code is in this class!
     * @param url URL to download the skin from
     * @param isSlim True if it is a slim skin, false if it is a classic skin
     * */
    private SkinData(@NotNull String url, boolean isSlim, int priority, Player player, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        this.url = url;
        this.isSlim = isSlim;
        this.priority = priority;
        this.player = player;
        this.skullMeta = skullMeta;
        this.characterHead = characterHead;
        this.inventory = inventory;
        this.index = index;
        SneakyCharacterManager.getInstance().skinQueue.add(this, priority);
    }

    /**
     * Convert the supplied URL into a minecraft textured URL.
     * Minecraft does not allow external URLs as skin textures so making use of
     * MineSkin's API is the best way to convert the data!
     * */
    public void convertSkinURL() {
        this.attempts++;
        SneakyCharacterManager.getInstance().skinPreloader.requestsThisMinute++;
        //Make a request to MineSkin to change skin data!

        try(CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URL url = new URL(MINESKIN_API_URL);
            HttpPost request = new HttpPost(url.toURI());
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("variant", (isSlim ? "slim" : "classic"));
            requestBodyJson.put("name", "rp-char-skin");
            requestBodyJson.put("visibility", 0);
            requestBodyJson.put("url", this.url);

            StringEntity skinReq = new StringEntity(requestBodyJson.toString());
            request.addHeader("content-type", "application/json");
            request.addHeader("Authorization", "Bearer d5ec4e50664ef47655788f4b1409c0a47eaa5489598688a9bffeb865b8884882");
            request.setEntity(skinReq);
            HttpResponse response = httpClient.execute(request);

            if (response != null) {
                InputStream in = response.getEntity().getContent();
                JSONParser parser = new JSONParser();
                JSONObject result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));

                if (result.containsKey("delay")) {
                    return;
                }
                JSONObject dataObject = (JSONObject) result.get("data");
                if (dataObject == null) {
                    if (result.toString().contains("Too many requests")) SneakyCharacterManager.getInstance().skinQueue.pauseTicks = 40;
                    return;
                }
                JSONObject textureObject = (JSONObject) dataObject.get("texture");

                this.texture = (String) textureObject.get("value");
                this.signature = (String) textureObject.get("signature");
            }
        } catch (IOException | URISyntaxException | ParseException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("Something went very wrong!");
            return;
        }
        this.isValid = true;
    }

    /**
     * Get the ProfileProperty for the skin textures.
     * @return ProfileProperty with the new skin data | Null if skin failed to load!
     * */
    public @Nullable ProfileProperty getTextureProperty() {
        if (!isValid) return null;
        return new ProfileProperty("textures", texture, signature);
    }

    public void apply() {
        //Priority 0 is only used for pre-caching skins. So priority 0 skindatas should not be applied.
        if (this.priority > 0) {
            if (this.skullMeta == null) {
                this.player.setPlayerProfile(SkinUtil.handleCachedSkin(this.player, this.getTextureProperty()));
                Entity vehicle = player.getVehicle();
                if (vehicle != null) vehicle.removePassenger(player);
                player.teleport(player.getLocation().add(0, 1, 0));
            } else if (SneakyCharacterManager.getInstance().selectionMenu.menuExists(this.player.getUniqueId().toString())) {
                this.skullMeta.setPlayerProfile(SkinUtil.handleCachedSkin(this.player, this.getTextureProperty()));
                this.characterHead.setItemMeta(this.skullMeta);
                this.inventory.setItem(this.index, this.characterHead);
            }
        }
    }
    
    public String getUrl() {
        return url;
    }

    public boolean isValid() {
        return (isValid && !cancelled);
    }

    public boolean isProcessed() {
        return (isValid() || this.attempts > 5);
    }

    public Player getPlayer() {
        return player;
    }

    public void cancel() {
        if (this.isProcessed()) return;

        this.cancelled = true;
        this.remove();
    }

    public void remove() {
        SneakyCharacterManager.getInstance().skinQueue.remove(this);
        skinDataMap.values().removeIf(value -> value == this);
    }

    public static SkinData getOrCreate(@NotNull String url, boolean isSlim, int priority, Player player) {
        return skinDataMap.computeIfAbsent(url, key -> new SkinData(key, isSlim, priority, player, null, null, null, 0));
    }

    public static SkinData getOrCreate(@NotNull String url, boolean isSlim, int priority, Player player, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        return skinDataMap.computeIfAbsent(url, key -> new SkinData(key, isSlim, priority, player, skullMeta, characterHead, inventory, index));
    }

}
