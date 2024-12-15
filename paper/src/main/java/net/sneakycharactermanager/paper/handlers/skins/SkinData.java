package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SkinData extends BukkitRunnable {

    private final String url;
    private final boolean isSlim;
    private final int priority;
    final Player player;

    private final SkullMeta skullMeta;
    private final ItemStack characterHead;
    private final Inventory inventory;
    private final int index;

    private String texture;
    private String signature;
    private boolean isValid = false;
    private String jobid = null;

    private static final List<SkinData> skinDataList = new ArrayList<>();

    private static final String MINESKIN_QUEUE_URL = SneakyCharacterManager.getInstance().getConfig().getString("mineskinQueueUrl", "https://api.mineskin.org/v2/queue");
    private static final String MINESKIN_AUTH = SneakyCharacterManager.getInstance().getConfig().getString("mineskinAuth", null);
    private static final String USER_AGENT = "SneakyCharacterManager/1.0";

    /**
     * Load custom skill data from a normal web URL.
     * Things to note:
     * RUN THIS ASYNC. It will freeze the server while it makes an HTTP request and
     * so
     * to avoid over freezing the server with too many character changes using
     * "Bukkit.getAsyncScheduler().runNow(...)" is a good idea.
     * Example code is in this class!
     *
     * @param url    URL to download the skin from
     * @param isSlim True if it is a slim skin, false if it is a classic skin
     */
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
        skinDataList.add(this);
    }

    private SkinData(@NotNull String url, boolean isSlim, int priority, Player player) {
        this(url, isSlim, priority, player, null, null, null, 0);
    }

    /**
     * Convert the supplied URL into a minecraft textured URL.
     * Minecraft does not allow external URLs as skin textures so making use of
     * MineSkin's API is the best way to convert the data!
     */
    public void run() {
        if (MINESKIN_AUTH == null) {
            SneakyCharacterManager.getInstance().getLogger().warning("You must set a mineskin auth token in the config.yml file to apply skins.");
            this.cancel();
        }

        if (jobid == null) {
            SneakyCharacterManager.getInstance().skinPreloader.requestsThisMinute++;
            // Make a request to MineSkin to change skin data!

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                URL url = new URL(MINESKIN_QUEUE_URL);
                HttpPost request = new HttpPost(url.toURI());
                JSONObject requestBodyJson = new JSONObject();
                requestBodyJson.put("variant", (isSlim ? "slim" : "classic"));
                requestBodyJson.put("name", "rp-char-skin");
                requestBodyJson.put("visibility", "public");
                requestBodyJson.put("url", this.url);

                StringEntity skinReq = new StringEntity(requestBodyJson.toString());
                request.addHeader("Content-Type", "application/json");
                request.addHeader("Accept", "application/json");
                request.addHeader("User-Agent", USER_AGENT);
                request.addHeader("Authorization", "Bearer " + MINESKIN_AUTH);
                request.setEntity(skinReq);
                HttpResponse response = httpClient.execute(request);

                if (response != null) {
                    InputStream in = response.getEntity().getContent();
                    JSONParser parser = new JSONParser();
                    JSONObject result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));

                    JSONObject jobObject = (JSONObject) result.get("job");

                    if (jobObject == null || jobObject.get("id") == null) {
                        //SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API did not return a job ID.");
                        //this.cancel();
                    } else {
                        jobid = jobObject.get("id").toString();
                    }
                }
            } catch (IOException | URISyntaxException | ParseException e) {
                SneakyCharacterManager.getInstance().getLogger().severe("Something went very wrong!");
                e.printStackTrace();
                this.cancel();
            }
        } else {
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                // Create request
                HttpGet request = new HttpGet(MINESKIN_QUEUE_URL + "/" + jobid);
                request.addHeader("Accept", "application/json");
                request.addHeader("User-Agent", USER_AGENT);
                request.addHeader("Authorization", "Bearer " + MINESKIN_AUTH);

                // Send request
                HttpResponse response = httpClient.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    // Parse the JSON response
                    JSONParser parser = new JSONParser();
                    JSONObject jsonResponse = (JSONObject) parser.parse(new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));

                    Boolean success = (Boolean) jsonResponse.get("success");
                    if (success != null && success) {
                        JSONObject job = (JSONObject) jsonResponse.get("job");
                        String status = (String) job.get("status");

                        if (status.equals("completed")) {
                            // Extract skin details
                            JSONObject skin = (JSONObject) jsonResponse.get("skin");
                            JSONObject text = (JSONObject) skin.get("texture");
                            JSONObject data = (JSONObject) text.get("data");
                            texture = (String) data.get("value");
                            signature = (String) data.get("signature");

                            isValid = true;
                            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), this::apply);
                            this.cancel();
                        }
                    } else {
                        SneakyCharacterManager.getInstance().getLogger().severe("Mineskin API returned an Error: Job check failed or job does not exist.");
                        this.cancel();
                    }
                } else {
                    SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API returned status: " + statusCode);
                    this.cancel();
                }
            } catch (Exception e) {
                SneakyCharacterManager.getInstance().getLogger().severe("Something went very wrong!");
                e.printStackTrace();
                this.cancel();
            }
        }
    }

    @Override
    public void cancel() {
        skinDataList.remove(this);
        super.cancel();
    }

    /**
     * Get the ProfileProperty for the skin textures.
     *
     * @return ProfileProperty with the new skin data | Null if skin failed to load!
     */
    public @Nullable ProfileProperty getTextureProperty() {
        if (!isValid) return null;
        return new ProfileProperty("textures", texture, signature);
    }

    public void apply() {
        ProfileProperty property = this.getTextureProperty();
        if (property == null) return;

        if (this.player == null) return;

        // Priority 0 is used exclusively for pre-caching skins. So priority 0 skindatas
        // should not be applied.
        if (this.priority > 0) {
            if (this.skullMeta == null) {
                this.player.setPlayerProfile(SkinUtil.handleCachedSkin(this.player, property));
                Entity vehicle = player.getVehicle();
                if (vehicle != null) vehicle.removePassenger(player);
                player.teleport(player.getLocation().add(0, 1, 0));

                // Priority 3 is used exclusively for /skin updates
                if (this.priority == 3) {
                    Character character = Character.get(this.player);

                    if (character == null) return;

                    PlayerProfile profile = this.player.getPlayerProfile();
                    PlayerTextures textures = profile.getTextures();

                    if (textures.getSkin() == null) return;

                    String skinURL = textures.getSkin().toString();

                    SneakyCharacterManager.getInstance().getLogger().info("Skin Update: [" + this.player.getName() + "," + character.getNameUnformatted() + "," + skinURL + "]");

                    character.setSkin(skinURL);
                    character.setSlim(this.isSlim());
                    BungeeMessagingUtil.sendByteArray(this.player, "updateCharacter", this.player.getUniqueId().toString(), 1, skinURL, this.isSlim());

                    SkinCache.put(this.player.getUniqueId().toString(), skinURL, property);
                    return;
                }
            } else if (SneakyCharacterManager.getInstance().selectionMenu.menuExists(this.player.getUniqueId().toString())) {
                this.skullMeta.setPlayerProfile(SkinUtil.handleCachedSkin(this.player, property));
                this.characterHead.setItemMeta(this.skullMeta);
                this.inventory.setItem(this.index, this.characterHead);
            }
        }
        SkinCache.put(this.player.getUniqueId().toString(), this.url, property);
    }

    public String getUrl() {
        return url;
    }

    public boolean isSlim() {
        return isSlim;
    }

    public Player getPlayer() {
        return player;
    }

    public SkullMeta getSkullMeta() {
        return skullMeta;
    }

    public ItemStack getCharacterHead() {
        return characterHead;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getIndex() {
        return index;
    }

    public static SkinData getOrCreate(@NotNull String url, boolean isSlim, int priority, Player player) {
        for (SkinData skinData : skinDataList) {
            if (skinData.getUrl().equals(url) && skinData.isSlim() == isSlim && skinData.getIndex() == priority && skinData.getPlayer().equals(player) && skinData.getSkullMeta() == null && skinData.getCharacterHead() == null && skinData.getInventory() == null && skinData.index == 0) {
                return skinData;
            }
        }
        return new SkinData(url, isSlim, priority, player);
    }

    public static SkinData getOrCreate(@NotNull String url, boolean isSlim, int priority, Player player, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        for (SkinData skinData : skinDataList) {
            if (skinData.getUrl().equals(url) && skinData.isSlim() == isSlim && skinData.getIndex() == priority && skinData.getPlayer().equals(player) && skinData.getSkullMeta().equals(skullMeta) && skinData.getCharacterHead().equals(characterHead) && skinData.getInventory().equals(inventory) && skinData.index == index) {
                return skinData;
            }
        }
        return new SkinData(url, isSlim, priority, player, skullMeta, characterHead, inventory, index);
    }

}
