package net.sneakycharactermanager.paper.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.consolecommands.ConsoleCommandCharTemp;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.character.CharacterLoader;
import net.sneakycharactermanager.paper.util.BungeeMessagingUtil;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandSkin extends CommandBase {

    public CommandSkin() {
        super("skin");
        this.description = "Change your skin!";
        this.setUsage("/skin [URL (Must be direct image)] (slim/classic)");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Must be a player to run this command"));
            return false;
        }

        if (args.length < 1) {
            player.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return true;
        }

        if(args[0].equalsIgnoreCase("fetch")){
            Player target = player;
            if(args.length > 1){
                target = Bukkit.getPlayer(args[1]);
                if(target == null){
                    player.sendMessage(ChatUtility.convertToComponent("&cCould not find requested player!"));
                    return false;
                }
            }

            PlayerProfile profile = target.getPlayerProfile();
            PlayerTextures textures = profile.getTextures();
            URL skinURL = textures.getSkin();
            if(skinURL == null){
                player.sendMessage(ChatUtility.convertToComponent("&cPlayer doesn't not have a valid Skin URL!"));
                return false;
            }

            player.sendMessage(ChatUtility.convertToComponent("&eHere is " + target.getName() + "'s skin url! Click to Copy!"));
            player.sendMessage(ChatUtility.convertToComponent("&6" + skinURL)
                    .clickEvent(ClickEvent.copyToClipboard(skinURL.toString()))
                    .hoverEvent(HoverEvent.showText(ChatUtility.convertToComponent("&aClick to Copy!"))));
            return true;
        }

        if (ConsoleCommandCharTemp.isPlayerTempChar(player.getUniqueId().toString())) {
            player.sendMessage(ChatUtility.convertToComponent("&4You are currently on a template character, which do not support /nick and /skin."));
            return false;
        };

        Character character = Character.get(player);

        if (character == null) {
            player.sendMessage(ChatUtility.convertToComponent("&4You aren't on a character right now, so you cannot use /skin."));
            return false;
        };

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
            if (args[1].equalsIgnoreCase("slim")) slim = true;
            else slim = false;
        }

        player.sendMessage(ChatUtility.convertToComponent("&aUpdating your skin!"));

        CharacterLoader.updateSkin(player, url, slim);

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Arrays.asList("revert", "fetch");
        } else if(args.length == 2 && args[0].equalsIgnoreCase("fetch")){
            List<String> playerNames = new ArrayList<>();
            for (@NotNull Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase()) && !player.getName().equals("CMI-Fake-Operator")) playerNames.add(player.getName());
            }
            return playerNames;
        } else if (args.length == 2) {
            return Arrays.asList("slim", "classic");
        } else {
            return super.tabComplete(sender, alias, args, location); // or simply "return new ArrayList<>();"
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

                            if (character == null) return;

                            character.setSkin(textureURL);
                            character.setSlim(isSlim);
                            BungeeMessagingUtil.sendByteArray(player, "updateCharacter", player.getUniqueId().toString(), 1, textureURL, isSlim);
                            player.setPlayerProfile(profile);

                            Entity vehicle = player.getVehicle();
                            if (vehicle != null) vehicle.removePassenger(player);
                            player.teleport(player.getLocation().add(0, 1, 0));
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
