package me.bounser.nascraft.managers;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.images.ItemTextureProvider;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ImagesManager {

    private static ImagesManager instance;

    public static ImagesManager getInstance() { return instance == null ? instance = new ImagesManager() : instance; }


    public BufferedImage getImage(String identifier) {

        BufferedImage override = loadOverride(identifier);
        if (override != null) return override;

        Material material = resolveMaterial(identifier);
        if (material == null) {
            Nascraft.getInstance().getLogger().info("Unable to resolve material for image: " + identifier);
            return null;
        }

        BufferedImage image = ItemTextureProvider.getImage(material);
        if (image == null) {
            Nascraft.getInstance().getLogger().info("Unable to render texture for material: " + material.name().toLowerCase());
        }
        return image;
    }

    private BufferedImage loadOverride(String identifier) {
        File file = new File(Nascraft.getInstance().getDataFolder(), "images/" + identifier + ".png");
        if (!file.isFile()) return null;

        try (InputStream input = Files.newInputStream(file.toPath())) {
            return ImageIO.read(input);
        } catch (IOException ignored) {
            return null;
        } catch (IllegalArgumentException e) {
            Nascraft.getInstance().getLogger().info("Invalid argument for image: " + identifier);
            return null;
        }
    }

    private Material resolveMaterial(String identifier) {
        FileConfiguration items = Config.getInstance().getItemsFileConfiguration();

        String typeName = items.getString("items." + identifier + ".item-stack.type");
        if (typeName == null) {
            typeName = identifier.replaceAll("\\d", "");
        }

        try {
            return Material.matchMaterial(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static byte[] getBytesOfImage(BufferedImage image) {
        ByteArrayOutputStream baosBalance = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baosBalance);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baosBalance.toByteArray();
    }

}
