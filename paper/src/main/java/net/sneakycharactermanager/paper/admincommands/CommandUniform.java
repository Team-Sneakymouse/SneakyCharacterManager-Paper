package net.sneakycharactermanager.paper.admincommands;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.destroystokyo.paper.profile.PlayerProfile;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.Character;
import net.sneakycharactermanager.paper.handlers.skins.SkinData;
import net.sneakycharactermanager.paper.util.ChatUtility;

public class CommandUniform extends CommandBaseAdmin {

    private static final String IMGUR_API_URL = SneakyCharacterManager.getInstance().getConfig().getString("imgurApiUrl", "https://api.imgur.com/3/image");
    private static final String IMGUR_CLIENT_ID = SneakyCharacterManager.getInstance().getConfig().getString("imgurClientId", null);
    private static final String WEBSERVER_URL_PREFIX = SneakyCharacterManager.getInstance().getConfig().getString("webServerURLPrefix", null);
    private static final String WEBSERVER_PATH = SneakyCharacterManager.getInstance().getConfig().getString("webServerFolderPath", null);

    ConcurrentMap<String, File> uniforms = new ConcurrentHashMap<>();
    ConcurrentMap<String, File> uniforms_classic = new ConcurrentHashMap<>();
    ConcurrentMap<String, File> uniforms_slim = new ConcurrentHashMap<>();

    public CommandUniform() {
        super("uniform");
        this.description = "Apply a uniform to your current skin. The uniform will not be saved to your character.";
        this.setUsage("/" +  this.getName() + " [playerName] (uniform)");
        Bukkit.getScheduler().runTaskLater(SneakyCharacterManager.getInstance(), this::updateUniforms, 200);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if ((IMGUR_CLIENT_ID == null || IMGUR_CLIENT_ID.isEmpty()) && (WEBSERVER_URL_PREFIX == null || WEBSERVER_URL_PREFIX.isEmpty() || WEBSERVER_PATH == null || WEBSERVER_PATH.isEmpty())) {
            sender.sendMessage(ChatUtility.convertToComponent("&4You must set an imgur API client id or a webserver prefix and path in the config.yml file to use this command."));
            return false;
        }

        this.updateUniforms();

        if (args.length < 1) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return false;
        }
        
        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&aUnknown player: &b" + args[0]));
            return false;
        }

        Character character = Character.get(player);

        if (character == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&aThe player is not on a character: &b" + player.getName()));
            return false;
        }

        if (args.length == 1) {
            SkinData.getOrCreate(character.getSkin(), character.getSkinUUID(), character.isSlim(), 4, player, character.getCharacterUUID());
            return true;
        } else if (args.length > 2) {
            sender.sendMessage(ChatUtility.convertToComponent("&4Invalid Usage: " + this.getUsage()));
            return false;
        }

        File uniformFile;

        if (character.isSlim()) {
            uniformFile = uniforms_slim.get(args[1]);
        } else {
            uniformFile = uniforms_classic.get(args[1]);
        }
        
        if (uniformFile == null) {
            uniformFile = uniforms.get(args[1]);
        }

        if (uniformFile == null) {
            sender.sendMessage(ChatUtility.convertToComponent("&aInvalid uniform file: &b" + args[1]));
            return false;
        }

        PlayerProfile profile = player.getPlayerProfile();
        PlayerTextures textures = profile.getTextures();
        URL skinURL = textures.getSkin();

        if(skinURL == null){
            sender.sendMessage(ChatUtility.convertToComponent("&cPlayer doesn't not have a valid Skin URL!"));
            return false;
        }

        // Store as final so it can be used async
        File uniformFinal = uniformFile;
        
        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            try {
                // Grab character skin from remote web server
                HttpClient httpClient = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(
                    skinURL.toURI())
                    .timeout(Duration.ofSeconds(2))
                    .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                // Check the HTTP response status
                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    try (InputStream inputStream = response.body()) {
                        if (inputStream != null) {
                            // Grab the two images and overlay them
                            BufferedImage image = ImageIO.read(inputStream);
                            BufferedImage overlayImage = ImageIO.read(uniformFinal);

                            int width = Math.min(image.getWidth(), overlayImage.getWidth());
                            int height = Math.min(image.getHeight(), overlayImage.getHeight());

                            BufferedImage combined = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

                            for (int x = 0; x < width; x++) {
                                for (int y = 0; y < height; y++) {
                                    int pixelImage = image.getRGB(x, y);
                                    int pixelOverlay = overlayImage.getRGB(x, y);
                                    int outerAlpha = (pixelOverlay >> 24) & 0xFF;

                                    if (outerAlpha > 0) {
                                        // If the overlay image contains a pixel, definitely use this pixel
                                        combined.setRGB(x, y, pixelOverlay);
                                    } else {
                                        Point outerToInnerOffset = calculateOuterToInnerOffset(x, y);
                                        if (outerToInnerOffset == null) {
                                            // If the overlay image does not contain a pixel, and it is not an outer skin layer part, use the original pixel
                                            combined.setRGB(x, y, pixelImage);
                                        } else {
                                            int relativeInnerAlpha = (overlayImage.getRGB(x + (int) outerToInnerOffset.getX(), y + (int) outerToInnerOffset.getY()) >> 24) & 0xFF;

                                            if (relativeInnerAlpha == 0) {
                                                // If the overlay image does not contain a pixel, and its relative inner layer also does not, use the original pixel
                                                combined.setRGB(x, y, pixelImage);
                                            } else {
                                                // If the overlay image does not contain a pixel, and its relative inner layer does, use the overlay pixel (which is in this case empty)
                                                combined.setRGB(x, y, pixelOverlay);
                                            }
                                        }
                                    }
                                }
                            }

                            boolean succes = false;
                            String url = null;

                            if (WEBSERVER_PATH != null && !WEBSERVER_PATH.isEmpty() && WEBSERVER_URL_PREFIX != null && !WEBSERVER_URL_PREFIX.isEmpty()) {
                                String randomFileName = UUID.randomUUID() + ".png";
                                File outputFile = new File(WEBSERVER_PATH, randomFileName);

                                try {
                                    ImageIO.write(combined, "PNG", outputFile);
                                    succes = true;
                                    url = WEBSERVER_URL_PREFIX + randomFileName;

                                    Bukkit.getAsyncScheduler().runDelayed(SneakyCharacterManager.getInstance(), (z) -> {
                                        try {
                                            Files.deleteIfExists(outputFile.toPath());
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }, 600, TimeUnit.SECONDS);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return;
                                }
                            } else if (IMGUR_CLIENT_ID != null && !IMGUR_CLIENT_ID.isEmpty() && IMGUR_API_URL != null && !IMGUR_API_URL.isEmpty()) {
                                // Convert the merged images into a ByteArray
                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                ImageIO.write(combined, "png", byteArrayOutputStream);
                                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                                String dataImage = Base64.getEncoder().encodeToString(imageBytes);
                                String data = URLEncoder.encode("image", "UTF-8") + "="
                                        + URLEncoder.encode(dataImage, "UTF-8");

                                // Upload image to Imgur
                                URL imgurUrl = new URL(IMGUR_API_URL);
                                HttpURLConnection imgurConnection = (HttpURLConnection) imgurUrl.openConnection();
                                imgurConnection.setRequestMethod("POST");
                                imgurConnection.setDoOutput(true);
                                imgurConnection.setRequestProperty("Authorization", "Client-ID " + IMGUR_CLIENT_ID);
                                imgurConnection.setRequestMethod("POST");
                                imgurConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                                imgurConnection.connect();

                                OutputStreamWriter wr = new OutputStreamWriter(imgurConnection.getOutputStream());
                                wr.write(data);
                                wr.flush();

                                // Check response and build response json
                                int imgurResponsecode = imgurConnection.getResponseCode();
                                if (imgurResponsecode == 200) {
                                    BufferedReader imgurReader = new BufferedReader(new InputStreamReader(imgurConnection.getInputStream()));
                                    StringBuilder imgurResponseBuilder = new StringBuilder();
                                    String line;
                                    while ((line = imgurReader.readLine()) != null) {
                                        imgurResponseBuilder.append(line);
                                    }

                                    String imgurResponse = imgurResponseBuilder.toString();

                                    // Grab the response URL
                                    JSONParser parser = new JSONParser();
                                    JSONObject imgurJson = (JSONObject) parser.parse(imgurResponse);
                                    JSONObject jsonData = (JSONObject) imgurJson.get("data");
                                    url = ((String) jsonData.get("link")).replace("\\", "");
                                    succes = true;
                                }
                            }

                            if (succes && url != null) {
                                // Make skindata and add to skinqueue
                                String urlFinal = new String(url);
                                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                                    SkinData.getOrCreate(urlFinal, "", character.isSlim(), 4, player, character.getCharacterUUID());
                                });
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                        Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                            sender.sendMessage(ChatUtility.convertToComponent("&aSomething went very wrong while applying this uniform."));
                        });
                    }
                } else {
                    Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                        sender.sendMessage(ChatUtility.convertToComponent("&aThe webserver that hosts the original skin could not be reached."));
                    });
                }
            } catch (IOException | InterruptedException | URISyntaxException e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(SneakyCharacterManager.getInstance(), () -> {
                    sender.sendMessage(ChatUtility.convertToComponent("&aSomething went very wrong while applying this uniform."));
                });
            }
        });
        
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase()) && !player.getName().equals("CMI-Fake-Operator")) playerNames.add(player.getName());
            }

            return playerNames;
        } else if (args.length == 2) {
            List<String> uniformNames = new ArrayList<>();

            for ( String name : uniforms.keySet()) {
                if (!uniformNames.contains(name)) uniformNames.add(name);
            }

            for ( String name : uniforms_classic.keySet()) {
                if (!uniformNames.contains(name)) uniformNames.add(name);
            }

            for ( String name : uniforms_slim.keySet()) {
                if (!uniformNames.contains(name)) uniformNames.add(name);
            }

            return uniformNames;
        } else {
            return new ArrayList<>();
        }
    }
    
    private void updateUniforms() {
        Bukkit.getAsyncScheduler().runNow(SneakyCharacterManager.getInstance(), (s) -> {
            this.uniforms_classic.clear();
            this.uniforms_slim.clear();
            this.uniforms.clear();
            for (File file : SneakyCharacterManager.getUniformFolder().listFiles()) {
                if (file.getName().toLowerCase().endsWith("_classic.png")) {
                    this.uniforms_classic.put(file.getName().replace("_classic.png", ""), file);
                } else if (file.getName().toLowerCase().endsWith("_slim.png")) {
                    this.uniforms_slim.put(file.getName().replace("_slim.png", ""), file);
                } else if (file.getName().toLowerCase().endsWith(".png")) {
                    this.uniforms.put(file.getName().replace(".png", ""), file);
                }
            }
        });
    }

    public static Point calculateOuterToInnerOffset(int x, int y) {
        if (y < 16 && x >= 32) {
            return new Point(-32, 0);
        } else if (y >= 32 && y < 48) {
            return new Point(0, -16);
        } else if (y >= 48 && x < 16) {
            return new Point(16, 0);
        } else if (y >= 48 && x >= 48) {
            return new Point(-16, 0);
        }

        return null;
    }

}
