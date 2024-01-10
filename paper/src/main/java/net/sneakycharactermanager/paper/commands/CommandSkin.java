package net.sneakycharactermanager.paper.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.util.ChatUtility;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class CommandSkin extends Command {

    public CommandSkin() {
        super("skin");
        this.description = "Change your skin!";
        this.setUsage("/skin <URL (Must be direct image)>");
        this.setPermission(SneakyCharacterManager.IDENTIFIER + ".command." + this.getName());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return true;
        }

        if(args[0].equalsIgnoreCase("default") || args[0].equalsIgnoreCase("revert")){
            player.sendMessage(ChatUtility.convertToComponent("&eFetching skin.. Please wait..!"));
            resetPlayerSkin(player);
            return true;
        }

        String url = args[0];
        if (!url.startsWith("http")) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid URL.. Please make sure it starts with HTTP(s)"));
            return true;
        }

        Boolean slim = null;
        if (args.length > 1) {
            if (args[1].equals("SLIM")) slim = true;
            else slim = false;
        }

        player.sendMessage(ChatUtility.convertToComponent("&aUpdating your skin!"));

        CharacterLoader.updateSkin(player, url, slim);

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Arrays.asList("revert");
        } else if (args.length == 2) {
            return Arrays.asList("SLIM", "CLASSIC");
        } else {
            return Collections.emptyList(); // or simply "return new ArrayList<>();"
        }
    }


    private void resetPlayerSkin(Player player){
        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s)->{
            try(CloseableHttpClient httpClient = HttpClientBuilder.create().build()){
                URL url = new URL(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false",
                        player.getUniqueId().toString().replace("-", "")));
                HttpGet httpGet = new HttpGet(url.toURI());
                HttpResponse response = httpClient.execute(httpGet);
                if(response != null){
                    InputStream in = response.getEntity().getContent();
                    JSONParser parser = new JSONParser();
                    JSONObject result = (JSONObject) parser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                    if(result.containsKey("properties")){

                        List<JSONObject> properties = (List<JSONObject>) result.get("properties");

                        JSONObject textureProp = properties.get(0);
                        if(!((String)textureProp.get("name")).equalsIgnoreCase("textures")) throw new RuntimeException("That didn't work...");

                        String textureValue = (String)textureProp.get("value");
                        String signatureValue = (String)textureProp.get("signature");
                        ProfileProperty property = new ProfileProperty("textures", textureValue, signatureValue);
                        Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), ()->{
                            PlayerProfile profile = player.getPlayerProfile();
                            profile.setProperty(property);
                            player.sendMessage(ChatUtility.convertToComponent("&aUpdating your skin!"));


                            String textureURL = profile.getTextures().getSkin().toString();
                            boolean isSlim = profile.getTextures().getSkinModel().equals(PlayerTextures.SkinModel.SLIM);
                            Character character = Character.get(player);
                            character.setSkin(textureURL);
                            character.setSlim(isSlim);
                            BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), 1, textureURL, isSlim);
                            player.setPlayerProfile(profile);

                            Entity vehicle = player.getVehicle();
                            if (vehicle != null) vehicle.removePassenger(player);
                        });
                    }
                }
            } catch(IOException | URISyntaxException | ParseException | RuntimeException exception){
                exception.printStackTrace();
                Bukkit.getLogger().severe("Something went very wrong!");
            }
        });
    }
    
    
}
