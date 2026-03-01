package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu.CharacterMenuHolder;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.apache.http.Header;
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
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

public class SkinData extends BukkitRunnable {

    private final String url;
    private String skinUUID;
    private String characterUUID;
    private final boolean isSlim;
    private int priority;
    final Player player;
    private final boolean updateSaveFile;

    private SkullMeta skullMeta;
    private ItemStack characterHead;
    private Inventory inventory;
    private int index;

    private String texture;
    private String signature;
    private boolean isValid = false;
    boolean processing = false;
    private String jobid = null;
    private long lastPollTime = 0;
    private String characterName = null;

    private String getQueueUrl() {
        return SneakyCharacterManager.getInstance().getConfig().getString("mineskinQueueUrl", "https://api.mineskin.org/v2/queue");
    }

    private String getSkinsUrl() {
        return SneakyCharacterManager.getInstance().getConfig().getString("mineskinSkinsUrl", "https://api.mineskin.org/v2/skins");
    }

    private String getUserAgent() {
        return SneakyCharacterManager.getInstance().getConfig().getString("mineskinUserAgent", "MineSkin-User-Agent");
    }

    private String getAuth() {
        return SneakyCharacterManager.getInstance().getConfig().getString("mineskinAuth", "");
    }

    private void addAuth(org.apache.http.client.methods.HttpRequestBase request) {
        String auth = getAuth();
        if (auth != null && !auth.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + auth);
        }
    }

    private String getAuthenticatedUrl(String baseUrl) {
        String auth = getAuth();
        if (auth != null && !auth.isEmpty()) {
            return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "key=" + auth;
        }
        return baseUrl;
    }

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
     * @param priority Priority of the skin in the queue.
     *                 0 = Preloading skins for offline players
     *                 1 = Preloading skins for online players
     *                 2 = Preloading skins for players that have opened their /char menu
     *                 3 = Loading a character
     *                 4 = Using /skin
     *                 5 = Using /uniform
     * @param player Player to apply the skin to
     * @param characterUUID UUID of the character
     * @param characterName Name of the character
     * @param skullMeta SkullMeta of the character
     * @param characterHead ItemStack of the character
     * @param inventory Inventory of the character
     * @param index Index of the character
     */
    private SkinData(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, String characterUUID, String characterName, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        this.url = url;
        this.skinUUID = skinUUID;
        this.isSlim = isSlim;
        this.priority = priority;
        this.player = player;
        this.characterUUID = characterUUID;
        this.characterName = characterName;
        this.skullMeta = skullMeta;
        this.characterHead = characterHead;
        this.inventory = inventory;
        this.index = index;
        this.updateSaveFile = priority >= SkinQueue.PRIO_LOAD && skinUUID.isEmpty();
        SneakyCharacterManager.getInstance().skinQueue.add(this, priority);
    }

    private SkinData(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, String characterUUID, String characterName) {
        this(url, skinUUID, isSlim, priority, player, characterUUID, characterName, null, null, null, 0);
    }

    /**
     * Convert the supplied URL into a minecraft textured URL.
     * Minecraft does not allow external URLs as skin textures so making use of
     * MineSkin's API is the best way to convert the data!
     */
    public boolean hasJobId() {
        return jobid != null;
    }

    public boolean isProcessing() {
        return processing;
    }

    @Override
    public void run() {
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        String auth = getAuth();
        if (auth == null || auth.isEmpty()) {
            SneakyCharacterManager.getInstance().getLogger().warning("You must set a mineskin auth token in the config.yml file to apply skins.");
            SneakyCharacterManager.getInstance().skinQueue.remove(this);
            return;
        }

        if (player == null || !player.isOnline()) {
            SneakyCharacterManager.getInstance().skinQueue.remove(this);
            return;
        }

        if (jobid == null) {
            if (!processing) return;

            if (skinUUID.isEmpty()) {
                // Make a request to MineSkin to queue a new skin
                try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                    if (debug) {
                        String keyPreview = (auth == null || auth.isEmpty()) ? "NONE" : auth.substring(0, Math.min(auth.length(), 4)) + "...";
                        SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Requesting queue with API Key: " + keyPreview);
                    }

                    String urlStr = getAuthenticatedUrl(getQueueUrl());
                    HttpPost request = new HttpPost(urlStr);
                    JSONObject requestBodyJson = new JSONObject();
                    requestBodyJson.put("variant", (isSlim ? "slim" : "classic"));
                    requestBodyJson.put("name", "rp-char-skin");
                    requestBodyJson.put("visibility", "public");
                    requestBodyJson.put("url", this.url);

                    StringEntity skinReq = new StringEntity(requestBodyJson.toString());
                    request.addHeader("Content-Type", "application/json");
                    request.addHeader("Accept", "application/json");
                    request.addHeader("User-Agent", getUserAgent());
                    addAuth(request);
                    request.setEntity(skinReq);
                    HttpResponse response = httpClient.execute(request);

                    if (response != null) {
                        String body = "";
                        JSONObject result = null;
                        try {
                            InputStream in = response.getEntity().getContent();
                            JSONParser parser = new JSONParser();
                            result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                            if (result != null) body = result.toJSONString();
                        } catch (Exception e) {
                            body = "Could not parse JSON body";
                        }

                        String headerLog = getHeadersString(response);
                        MineskinLogger.log(body, headerLog);
                        
                        if (debug) {
                            String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Raw Body Preview: " + preview);
                        }
                        


                        if (response.getStatusLine().getStatusCode() == 200 && result != null) {
                            JSONObject jobObject = (JSONObject) result.get("job");

                            if (jobObject == null || jobObject.get("id") == null) {
                                processing = false;
                            } else {
                                jobid = jobObject.get("id").toString();
                                SneakyCharacterManager.getInstance().skinQueue.recordGeneration();
                                if (debug) {
                                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Job Queued for " + player.getName() + ": " + jobid);
                                }
                            }
                        } else if (response.getStatusLine().getStatusCode() == 429) {
                             SneakyCharacterManager.getInstance().getLogger().warning("MineSkin API rate limit hit (429) during queue request.");
                        } else if (response.getStatusLine().getStatusCode() != 200) {
                             int code = response.getStatusLine().getStatusCode();
                             SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API returned status: " + code + " during queue request.");
                             player.sendMessage(ChatUtility.convertToComponent("&cFailed to generate skin (MineSkin error " + code + "). The URL may be invalid or unsupported."));
                             SneakyCharacterManager.getInstance().skinQueue.remove(this);
                        }
                    }
                } catch (IOException e) {
                    SneakyCharacterManager.getInstance().getLogger().severe("Something went very wrong!");
                    e.printStackTrace();
                    this.cancel();
                }
            } else {
                // Make a request to MineSkin to re-request an old job.
                try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                    if (debug) {
                        String keyPreview = (auth == null || auth.isEmpty()) ? "NONE" : auth.substring(0, Math.min(auth.length(), 4)) + "...";
                        SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Requesting skin fetch for " + player.getName() + " with API Key: " + keyPreview + " | UUID: " + skinUUID);
                    }

                    // Create request
                    String urlStr = getAuthenticatedUrl(getSkinsUrl() + "/" + skinUUID);
                    HttpGet request = new HttpGet(urlStr);
                    request.addHeader("Accept", "application/json");
                    request.addHeader("User-Agent", getUserAgent());
                    addAuth(request);

                    // Send request
                    HttpResponse response = httpClient.execute(request);
                    int statusCode = response.getStatusLine().getStatusCode();

                    String body = "";
                    JSONObject jsonResponse = null;
                    try {
                        InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8);
                        JSONParser parser = new JSONParser();
                        jsonResponse = (JSONObject) parser.parse(reader);
                        if (jsonResponse != null) body = jsonResponse.toJSONString();
                    } catch (Exception e) {
                        body = "Could not parse JSON body";
                    }

                    String headerLog = getHeadersString(response);
                    MineskinLogger.log(body, headerLog);
                    
                    if (debug) {
                        String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                        SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Raw Body Preview: " + preview);
                    }
                    


                    if (statusCode == 200 && jsonResponse != null) {
                        Boolean success = (Boolean) jsonResponse.get("success");
                        if (success != null && success) {
                            JSONObject skin = (JSONObject) jsonResponse.get("skin");
                            JSONObject text = (JSONObject) skin.get("texture");
                            JSONObject data = (JSONObject) text.get("data");
                            texture = (String) data.get("value");
                            signature = (String) data.get("signature");

                            isValid = true;
                            this.apply();
                        } else {
                            SneakyCharacterManager.getInstance().getLogger().severe("Mineskin API returned an Error: Job check failed or job does not exist.");
                            SneakyCharacterManager.getInstance().skinQueue.remove(this);
                        }
                    } else if (statusCode == 429) {
                        SneakyCharacterManager.getInstance().getLogger().warning("MineSkin API rate limit hit (429) during skin fetch.");
                    } else if (statusCode != 200) {
                        SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API returned status: " + statusCode + " during skin fetch.");
                        this.cancel();
                    }
                } catch (Exception e) {
                    SneakyCharacterManager.getInstance().getLogger().severe("Something went very wrong!");
                    e.printStackTrace();
                    this.cancel();
                }
            }
        } else {
            // Check up on the status of a queued job and apply it if able.
            lastPollTime = System.currentTimeMillis();
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                if (debug) {
                    String keyPreview = (auth == null || auth.isEmpty()) ? "NONE" : auth.substring(0, Math.min(auth.length(), 4)) + "...";
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Requesting job poll for " + player.getName() + " with API Key: " + keyPreview + " | JobID: " + jobid);
                }

                // Create request
                String urlStr = getAuthenticatedUrl(getQueueUrl().replace("/url", "") + "/" + jobid);
                HttpGet request = new HttpGet(urlStr);
                request.addHeader("Accept", "application/json");
                request.addHeader("User-Agent", getUserAgent());
                addAuth(request);

                // Send request
                HttpResponse response = httpClient.execute(request);
                int statusCode = response.getStatusLine().getStatusCode();

                String body = "";
                JSONObject jsonResponse = null;
                try {
                    InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8);
                    JSONParser parser = new JSONParser();
                    jsonResponse = (JSONObject) parser.parse(reader);
                    if (jsonResponse != null) body = jsonResponse.toJSONString();
                } catch (Exception e) {
                    body = "Could not parse JSON body";
                }

                String headerLog = getHeadersString(response);
                MineskinLogger.log(body, headerLog);
                
                if (debug) {
                    String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Raw Body Preview: " + preview);
                }
                


                if (statusCode == 200 && jsonResponse != null) {
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
                            skinUUID = (String) job.get("result");

                            isValid = true;
                            this.apply();
                        }
                    } else {
                        SneakyCharacterManager.getInstance().getLogger().severe("Mineskin API returned an Error: Job check failed or job does not exist.");
                        SneakyCharacterManager.getInstance().skinQueue.remove(this);
                    }
                } else if (statusCode == 429) {
                    SneakyCharacterManager.getInstance().getLogger().warning("MineSkin API rate limit hit (429) during job poll.");
                } else if (statusCode != 200) {
                    SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API returned status: " + statusCode + " during job poll.");
                    SneakyCharacterManager.getInstance().skinQueue.remove(this);
                }
            } catch (Exception e) {
                SneakyCharacterManager.getInstance().getLogger().severe("Something went very wrong!");
                e.printStackTrace();
                SneakyCharacterManager.getInstance().skinQueue.remove(this);
            }
        }
    }

    @Override
    public void cancel() {
        SneakyCharacterManager.getInstance().skinQueue.remove(this);
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

        // Apply must run on main thread
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), this::apply);
            return;
        }

        // Apply to player if:
        // - It's a character load/manual update (priority >= PRIO_LOAD) even if it was originally a menu item
        if (this.priority >= SkinQueue.PRIO_LOAD) {
            this.player.setPlayerProfile(SkinUtil.handleCachedSkin(this.player, property));
            Entity vehicle = player.getVehicle();
            if (vehicle != null) vehicle.removePassenger(player);
            player.teleport(player.getLocation().add(0, 1, 0));

            // PRIO_SKIN is used exclusively for /skin
            if (priority == SkinQueue.PRIO_SKIN) {
                Character character = Character.get(this.player);

                if (character == null) {
                    SneakyCharacterManager.getInstance().skinQueue.remove(this);
                    return;
                }

                PlayerProfile profile = this.player.getPlayerProfile();
                PlayerTextures textures = profile.getTextures();

                if (textures.getSkin() == null) {
                    SneakyCharacterManager.getInstance().skinQueue.remove(this);
                    return;
                }

                String skinURL = textures.getSkin().toString();

                SneakyCharacterManager.getInstance().getLogger().info("Skin Update: [" + this.player.getName() + "," + character.getNameUnformatted() + "," + skinURL + "]");

                character.setSkin(skinURL);
                character.setSlim(this.isSlim());
                BungeeMessagingUtil.sendByteArray(this.player, "updateCharacter", this.player.getUniqueId().toString(), characterUUID, 1, skinURL, skinUUID, this.isSlim());
            }
        }

        SkinCache.put(this.player.getUniqueId().toString(), this.url, property);

        // Apply the skin to the /char menu if there is one
        if (isMenuOpen()) {
            CharacterMenuHolder holder = SneakyCharacterManager.getInstance().selectionMenu.activeMenus.get(this.player.getUniqueId().toString());
            holder.displayCurrentPage();
        }

        if (updateSaveFile) {
            SneakyCharacterManager.getInstance().getLogger().info("Skin Update: [" + this.player.getName() + "," + player.getName() + "," + url + "]");
            BungeeMessagingUtil.sendByteArray(this.player, "updateCharacter", this.player.getUniqueId().toString(), characterUUID, 1, url, skinUUID, this.isSlim());
        }

        SneakyCharacterManager.getInstance().skinQueue.remove(this);
    }

    public String getUrl() {
        return url;
    }

    public String getSkinUUID() {
        return skinUUID;
    }

    public String getCharacterUUID() {
        return characterUUID;
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

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public String getJobId() {
        return jobid;
    }

    public long getLastPollTime() {
        return lastPollTime;
    }

    public String getCharacterName() {
        return characterName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", url);
        map.put("skinUUID", skinUUID);
        map.put("isSlim", isSlim);
        map.put("priority", priority);
        map.put("player", player != null ? player.getName() : "null");
        map.put("characterName", characterName != null ? ChatUtility.stripFormatting(characterName) : "null");
        map.put("characterUUID", characterUUID);
        map.put("processing", processing);
        map.put("jobid", jobid);
        map.put("lastPollTime", lastPollTime);
        return map;
    }

    public void updateMeta(SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        this.skullMeta = skullMeta;
        this.characterHead = characterHead;
        this.inventory = inventory;
        this.index = index;
    }

    /** True if this entry has an associated menu AND the player currently has it open. */
    public boolean isMenuOpen() {
        if (inventory == null || player == null || !player.isOnline()) return false;
        return inventory.equals(player.getOpenInventory().getTopInventory());
    }

    public static SkinData getOrCreate(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, String characterUUID, String characterName) {
        List<SkinData> skinDataList = SneakyCharacterManager.getInstance().skinQueue.getQueue().values().stream().flatMap(List::stream).toList();
        
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        if (debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] getOrCreate (7-arg) search for " + player.getName() + " | Char: " + characterUUID + " | URL: " + url.substring(Math.max(0, url.length() - 20)));
        }

        for (SkinData skinData : skinDataList) {
            String existingUrl = skinData.getUrl();
            UUID existingPlayerId = skinData.getPlayer().getUniqueId();
            String existingCharId = skinData.getCharacterUUID();

            if (existingUrl.equals(url) && existingPlayerId.equals(player.getUniqueId()) && 
                Objects.equals(existingCharId, characterUUID)) {
                
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] MATCH FOUND for " + characterName + " (Existing P" + skinData.getPriority() + " -> New P" + priority + ")");
                }

                if (priority > skinData.getPriority()) {
                    SneakyCharacterManager.getInstance().skinQueue.updatePriority(player, characterUUID, priority);
                }
                return skinData;
            }
        }

        if (debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] NO MATCH - Creating new SkinData for " + characterName + " (P" + priority + ")");
        }
        return new SkinData(url, skinUUID, isSlim, priority, player, characterUUID, characterName);
    }

    public static SkinData getOrCreate(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, String characterUUID, String characterName, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        List<SkinData> skinDataList = SneakyCharacterManager.getInstance().skinQueue.getQueue().values().stream().flatMap(List::stream).toList();
        
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        if (debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] getOrCreate search for " + player.getName() + " | Char: " + characterUUID + " | URL: " + url.substring(Math.max(0, url.length() - 20)));
        }

        for (SkinData skinData : skinDataList) {
            String existingUrl = skinData.getUrl();
            UUID existingPlayerId = skinData.getPlayer().getUniqueId();
            String existingCharId = skinData.getCharacterUUID();

            if (existingUrl.equals(url) && existingPlayerId.equals(player.getUniqueId()) && 
                Objects.equals(existingCharId, characterUUID)) {
                
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] MATCH FOUND for " + characterName + " (Existing P" + skinData.getPriority() + " -> New P" + priority + ")");
                }

                // Update meta if this is for a menu display
                skinData.updateMeta(skullMeta, characterHead, inventory, index);

                if (priority > skinData.getPriority()) {
                    SneakyCharacterManager.getInstance().skinQueue.updatePriority(player, characterUUID, priority);
                }
                return skinData;
            }
        }
        
        if (debug) {
             SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] NO MATCH - Creating new SkinData for " + characterName + " (P" + priority + ")");
        }
        return new SkinData(url, skinUUID, isSlim, priority, player, characterUUID, characterName, skullMeta, characterHead, inventory, index);
    }


    private String getHeadersString(HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        for (Header header : response.getAllHeaders()) {
            sb.append(header.getName()).append(": ").append(header.getValue()).append("; ");
        }
        return sb.toString();
    }

}
