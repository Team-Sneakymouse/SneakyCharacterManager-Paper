package net.sneakycharactermanager.paper.handlers.skins;

import com.destroystokyo.paper.profile.ProfileProperty;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R2.profile.CraftPlayerTextures;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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

                    for(Player target : Bukkit.getOnlinePlayers()){
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

    private String texture;
    private String signature;
    private boolean isValid;

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
    public SkinData(@NotNull String url, boolean isSlim){
        this.url = url;
        this.isSlim = isSlim;

        convertSkinURL();

        //ToDo: Maybe local cache of the skins to save for the future?
    }

    /**
     * Convert the supplied URL into a minecraft textured URL.
     * Minecraft does not allow external URLs as skin textures so making use of
     * MineSkin's API is the best way to convert the data!
     * */
    private void convertSkinURL() {
        //Make a request to MineSkin to change skin data!

        try(CloseableHttpClient httpClient = HttpClientBuilder.create().build()){
            URL url = new URL(MINESKIN_API_URL);
            HttpPost request = new HttpPost(url.toURI());
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("variant", (isSlim ? "slim" : "classic"));
            requestBodyJson.put("name", "rp-char-skin");
            requestBodyJson.put("visibility", 0);
            requestBodyJson.put("url", this.url);

            StringEntity skinReq = new StringEntity(requestBodyJson.toString());
            request.addHeader("content-type", "application/json");
            request.setEntity(skinReq);
            HttpResponse response = httpClient.execute(request);

            if(response != null){
                InputStream in = response.getEntity().getContent();
                JSONParser parser = new JSONParser();
                JSONObject result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                JSONObject dataObject = (JSONObject) result.get("data");
                JSONObject textureObject = (JSONObject) dataObject.get("texture");

                this.texture = (String) textureObject.get("value");
                this.signature = (String) textureObject.get("signature");
            }

        } catch (IOException | URISyntaxException | ParseException e){
            e.printStackTrace();
            this.isValid = false;
            Bukkit.getLogger().severe("Something went very wrong!");
        }
        this.isValid = true;

    }

    /**
     * Get the ProfileProperty for the skin textures.
     * @return ProfileProperty with the new skin data | Null if skin failed to load!
     * */
    public @Nullable ProfileProperty getTextureProperty(){
        if(!isValid) return null;
        return new ProfileProperty("textures", texture, signature);
    }

}
