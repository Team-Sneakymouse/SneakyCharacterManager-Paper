package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.api.character.CharacterKey;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterSelectionMenu.CharacterMenuHolder;
import net.sneakycharactermanager.paper.util.ProxyMessagingUtil;
import net.sneakycharactermanager.paper.util.SkinUtil;
import com.sneakyutils.util.TextUtility;
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
    /** The character this skin is for (owner + ID). Fixed at creation: results are saved here, never to whatever the player wears later. */
    private final CharacterKey characterKey;
    private final boolean isSlim;
    private int priority;
    final Player player;

    private SkullMeta skullMeta;
    private ItemStack characterHead;
    private Inventory inventory;
    private int index;

    private String texture;
    private String signature;
    private boolean isValid = false;
    volatile boolean processing = false;
    private volatile String jobid = null;
    private volatile long lastPollTime = 0;
    private String characterName = null;

    private String baseSkinUrl;
    private String uniformHash;
    /** Map key / file stem for display; null uses {@code "Uniform"} in skin state names. */
    private String uniformKey;
    /** When set and priority is {@link SkinQueue#PRIO_SKIN}, used as {@link SkinState} name instead of {@code "Regular"}. */
    @Nullable private String skinStateLabel;
    /** Content id from proxy resolve (MISS); used when registering after MineSkin. */
    private String pendingSkinId = "";

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
     * @param player Player that requested the skin and gets it applied (if it still wears the character)
     * @param characterKey The character the skin is for
     * @param characterName Name of the character
     * @param skullMeta SkullMeta of the character
     * @param characterHead ItemStack of the character
     * @param inventory Inventory of the character
     * @param index Index of the character
     */
    private SkinData(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, CharacterKey characterKey, String characterName, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        this.url = url;
        this.skinUUID = skinUUID;
        this.isSlim = isSlim;
        this.priority = priority;
        this.player = player;
        this.characterKey = characterKey;
        this.characterName = characterName;
        this.skullMeta = skullMeta;
        this.characterHead = characterHead;
        this.inventory = inventory;
        this.index = index;
        SneakyCharacterManager.getInstance().skinQueue.add(this, priority);
    }

    private SkinData(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, CharacterKey characterKey, String characterName) {
        this(url, skinUUID, isSlim, priority, player, characterKey, characterName, null, null, null, 0);
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

    /** Reports response metadata; the queue alone decides when this entry may run again. */
    private void reportRateLimit(HttpResponse response, JSONObject body) {
        parseAndUpdateRateLimit(body);
        Header retryAfter = response.getFirstHeader("Retry-After");
        SneakyCharacterManager.getInstance().skinQueue.rateLimited(this,
                retryAfter == null ? null : retryAfter.getValue());
    }

    /**
     * Parse the rateLimit section from a queue/job-poll API response and sync
     * our local capacity state with the values MineSkin reports.
     */
    private void parseAndUpdateRateLimit(JSONObject response) {
        if (response == null) return;
        if (!(response.get("rateLimit") instanceof JSONObject rateLimit)) return;

        int limitValue = -1, remainingValue = -1, delayMillisValue = -1;
        long resetTime = 0;

        if (rateLimit.get("limit") instanceof JSONObject limitObj) {
            if (limitObj.get("limit") instanceof Number value) limitValue = value.intValue();
            if (limitObj.get("remaining") instanceof Number value) remainingValue = value.intValue();
            if (limitObj.get("reset") instanceof Number value) {
                resetTime = value.longValue();
                // Convert Unix seconds → millis if needed
                if (resetTime > 0 && resetTime < 10_000_000_000L) resetTime *= 1000L;
            }
        }

        if (rateLimit.get("delay") instanceof JSONObject delay
                && delay.get("millis") instanceof Number value) {
            delayMillisValue = value.intValue();
        }

        // Only track a future reset time — if it's past, the window already rolled over.
        long futureReset = (resetTime > System.currentTimeMillis()) ? resetTime : 0;
        SneakyCharacterManager.getInstance().skinQueue.updateRateLimit(limitValue, remainingValue, futureReset, delayMillisValue);
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

                        int statusCode = response.getStatusLine().getStatusCode();
                        if ((statusCode == 200 || statusCode == 202) && result != null) {
                            JSONObject jobObject = (JSONObject) result.get("job");

                            parseAndUpdateRateLimit(result);
                            if (jobObject == null || jobObject.get("id") == null) {
                                processing = false;
                            } else {
                                jobid = jobObject.get("id").toString();
                                SneakyCharacterManager.getInstance().skinQueue.requestSucceeded(this);
                                if (debug) {
                                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] Job Queued for " + player.getName() + ": " + jobid);
                                }
                            }
                        } else if (statusCode == 429) {
                             SneakyCharacterManager.getInstance().getLogger().warning("MineSkin API rate limit hit (429) during queue request.");
                             reportRateLimit(response, result);
                        } else {
                             SneakyCharacterManager.getInstance().getLogger().severe("MineSkin API returned status: " + statusCode + " during queue request.");
                             player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cFailed to generate skin (MineSkin error " + statusCode + "). The URL may be invalid or unsupported."));
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
                        reportRateLimit(response, jsonResponse);
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
                    SneakyCharacterManager.getInstance().skinQueue.requestSucceeded(this);
                    parseAndUpdateRateLimit(jsonResponse);
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
                        } else if (status.equals("failed")) {
                            SneakyCharacterManager.getInstance().getLogger().warning("MineSkin job " + jobid + " failed for " + player.getName() + ".");
                            player.sendMessage(TextUtility.INSTANCE.convertToComponent("&cFailed to generate skin. The URL may be invalid or unsupported."));
                            SneakyCharacterManager.getInstance().skinQueue.remove(this);
                        }
                    } else {
                        SneakyCharacterManager.getInstance().getLogger().severe("Mineskin API returned an Error: Job check failed or job does not exist.");
                        SneakyCharacterManager.getInstance().skinQueue.remove(this);
                    }
                } else if (statusCode == 429) {
                    SneakyCharacterManager.getInstance().getLogger().warning("MineSkin API rate limit hit (429) during job poll.");
                    reportRateLimit(response, jsonResponse);
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
        // - and the player still wears this character. A result for a character it has switched away from is only
        //   saved to that character (below), never put on the body or onto the character it wears now.
        if (this.priority >= SkinQueue.PRIO_LOAD) {
            boolean isUniform = this.uniformHash != null;
            boolean worn = Character.isWearing(this.player, this.characterKey);

            // EXCEPTION: Don't apply a uniform variant automatically during character load (PRIO_LOAD).
            // It should only be applied if priority is PRIO_UNIFORM (4).
            if (worn && (!isUniform || this.priority == SkinQueue.PRIO_UNIFORM)) {
                SkinUtil.applySkin(this.player, property);

                SkinStateManager mgr = SneakyCharacterManager.getInstance().skinStateManager;
                String stateName;
                if (isUniform) {
                    stateName = SkinStateManager.uniformKeyToDisplayName(this.uniformKey);
                } else if (this.priority == SkinQueue.PRIO_SKIN && this.skinStateLabel != null) {
                    stateName = this.skinStateLabel;
                } else {
                    stateName = "Regular";
                }
                SkinState state = mgr.record(this.player, stateName, texture, signature, this.characterKey, this.url, isUniform);

                if (this.priority == SkinQueue.PRIO_SKIN || this.priority == SkinQueue.PRIO_UNIFORM) {
                    SkinStateManager.sendSkinUpdatedMessage(this.player, state);
                }
            }

            if (!worn && !isUniform) {
                // The record gets this skin below; older session states must not override it on the next load.
                SneakyCharacterManager.getInstance().skinStateManager.forgetCharacter(this.player.getUniqueId(), this.characterKey);
            }

            // PRIO_SKIN is used exclusively for /skin
            if (priority == SkinQueue.PRIO_SKIN && worn) {
                Character character = Character.get(this.player);

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
                character.setTexture(texture);
                character.setSignature(signature);
            }
        }

        SkinApplyService.onMineSkinApplied(this, property);

        // Apply the skin to the /char menu if there is one
        if (isMenuOpen()) {
            CharacterMenuHolder holder = SneakyCharacterManager.getInstance().selectionMenu.activeMenus.get(this.player.getUniqueId().toString());
            holder.displayCurrentPage();
        }

        if (this.uniformHash == null && priority == SkinQueue.PRIO_LOAD) {
            SneakyCharacterManager.getInstance().getLogger().info("Skin Update (Cache Persistence): [" + this.player.getName() + "," + characterName + "," + url + "]");
        }

        // Notify proxy of new uniform variant
        if (baseSkinUrl != null && uniformHash != null) {
            String textureUrl = SkinUtil.getTextureUrl(property);
            if (textureUrl != null) {
                ProxyMessagingUtil.sendByteArray(this.player, "saveUniformVariant", baseSkinUrl, uniformHash, skinUUID, textureUrl, texture, signature);
            }
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
        return characterKey.characterId();
    }

    public CharacterKey getCharacterKey() {
        return characterKey;
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
        map.put("characterUUID", characterKey.characterId());
        map.put("owner", characterKey.owner().toString());
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

    public void setUniformCacheInfo(String baseSkinUrl, String uniformHash) {
        setUniformCacheInfo(baseSkinUrl, uniformHash, null);
    }

    public void setUniformCacheInfo(String baseSkinUrl, String uniformHash, @Nullable String uniformKey) {
        this.baseSkinUrl = baseSkinUrl;
        this.uniformHash = uniformHash;
        this.uniformKey = uniformKey;
    }

    public String getUniformHash() {
        return uniformHash;
    }

    public void setPendingSkinId(String pendingSkinId) {
        this.pendingSkinId = pendingSkinId == null ? "" : pendingSkinId;
    }

    public String getPendingSkinId() {
        return pendingSkinId;
    }

    /**
     * Optional label for the recorded {@link SkinState} when this job uses {@link SkinQueue#PRIO_SKIN}.
     * Pass {@code null} or blank to keep the default {@code "Regular"} name.
     */
    public void setSkinStateLabel(@Nullable String skinStateLabel) {
        this.skinStateLabel = (skinStateLabel != null && !skinStateLabel.isBlank()) ? skinStateLabel.trim() : null;
    }

    /** True if this entry has an associated menu AND the player currently has it open. */
    public boolean isMenuOpen() {
        if (inventory == null || player == null || !player.isOnline()) return false;
        return inventory.equals(player.getOpenInventory().getTopInventory());
    }

    /**
     * Returns the queued job for this skin URL, requester and character, or queues a new one. The character is matched by
     * owner and ID, so a job for another owner's character with the same ID is never reused.
     */
    public static SkinData getOrCreate(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, CharacterKey characterKey, String characterName) {
        if (url == null || url.isEmpty()) return null;
        SkinData existing = find(url, player, characterKey, priority, characterName);
        if (existing != null) return existing;
        return new SkinData(url, skinUUID, isSlim, priority, player, characterKey, characterName);
    }

    public static SkinData getOrCreate(@NotNull String url, @NotNull String skinUUID, boolean isSlim, int priority, Player player, CharacterKey characterKey, String characterName, SkullMeta skullMeta, ItemStack characterHead, Inventory inventory, int index) {
        if (url == null || url.isEmpty()) return null;
        SkinData existing = find(url, player, characterKey, priority, characterName);
        if (existing != null) {
            // Update meta if this is for a menu display
            existing.updateMeta(skullMeta, characterHead, inventory, index);
            return existing;
        }
        return new SkinData(url, skinUUID, isSlim, priority, player, characterKey, characterName, skullMeta, characterHead, inventory, index);
    }

    @Nullable
    private static SkinData find(String url, Player player, CharacterKey characterKey, int priority, String characterName) {
        boolean debug = SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.debug", false);
        if (debug) {
            String urlEnd = url.length() > 20 ? url.substring(url.length() - 20) : url;
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] getOrCreate search for " + player.getName() + " | Char: " + characterKey + " | URL: " + urlEnd);
        }

        for (SkinData skinData : SneakyCharacterManager.getInstance().skinQueue.getQueue().values().stream().flatMap(List::stream).toList()) {
            if (skinData.getUrl().equals(url) && skinData.getPlayer().getUniqueId().equals(player.getUniqueId())
                    && characterKey.equals(skinData.getCharacterKey())) {
                if (debug) {
                    SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] MATCH FOUND for " + characterName + " (Existing P" + skinData.getPriority() + " -> New P" + priority + ")");
                }
                SneakyCharacterManager.getInstance().skinQueue.raise(skinData, priority);
                return skinData;
            }
        }

        if (debug) {
            SneakyCharacterManager.getInstance().getLogger().info("[SkinQueue Debug] NO MATCH - Creating new SkinData for " + characterName + " (P" + priority + ")");
        }
        return null;
    }


    private String getHeadersString(HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        for (Header header : response.getAllHeaders()) {
            sb.append(header.getName()).append(": ").append(header.getValue()).append("; ");
        }
        return sb.toString();
    }

}
