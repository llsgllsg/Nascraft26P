package me.bounser.nascraft.images;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemTextureProvider {

    private static volatile JarFile clientJar;
    private static volatile boolean hasTextures = false;
    private static String jarAssetPrefix = "";

    private static final Object JAR_LOCK = new Object();
    private static final ConcurrentHashMap<String, BufferedImage> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, byte[]> BYTES_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSES = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.CountDownLatch READY = new java.util.concurrent.CountDownLatch(1);

    private ItemTextureProvider() {}

    public static void init(JavaPlugin plugin) {
        File nascraftCache = new File(plugin.getDataFolder(), "cache");
        nascraftCache.mkdirs();

        File reused = findCachedJar(nascraftCache);
        if (reused != null) {
            JarFile jf = openJar(reused);
            if (jf != null) {
                probeJarStructure(jf);
                if (hasTextures) {
                    clientJar = jf;
                    plugin.getLogger().info("[Textures] Loaded cached client JAR: " + reused.getName());
                    READY.countDown();
                    return;
                }
                try { jf.close(); } catch (Exception ignored) {}
            }
        }

        Thread t = new Thread(() -> {
            try {
                downloadLatestClientJar(plugin, nascraftCache);
            } finally {
                READY.countDown();
            }
        }, "nascraft-jar-dl");
        t.setDaemon(true);
        t.start();
    }

    public static boolean awaitReady(long timeoutMs) {
        try {
            return READY.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) && hasTextures;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static File findCachedJar(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null) return null;
        File newest = null;
        long best = Long.MIN_VALUE;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("client_") && f.getName().endsWith(".jar") && f.lastModified() > best) {
                newest = f;
                best = f.lastModified();
            }
        }
        return newest;
    }

    public static void close() {
        JarFile jf = clientJar;
        if (jf != null) {
            try { jf.close(); } catch (Exception ignored) {}
        }
        clientJar = null;
        TEXTURE_CACHE.clear();
        BYTES_CACHE.clear();
        MISSES.clear();
    }

    private static JarFile openJar(File f) {
        try { return new JarFile(f); } catch (Exception e) { return null; }
    }

    private static void probeJarStructure(JarFile jar) {
        String[] probeTargets = { "diamond.png", "iron_ingot.png", "coal.png" };
        Enumeration<JarEntry> en = jar.entries();
        List<String> names = new ArrayList<>();
        while (en.hasMoreElements()) names.add(en.nextElement().getName());

        for (String target : probeTargets) {
            for (String name : names) {
                if (name.endsWith("item/" + target) || name.endsWith("item\\" + target)) {
                    int idx = name.indexOf("assets/minecraft/");
                    jarAssetPrefix = idx > 0 ? name.substring(0, idx) : "";
                    hasTextures = true;
                    return;
                }
            }
        }
        hasTextures = false;
    }

    private static void downloadLatestClientJar(JavaPlugin plugin, File cacheDir) {
        try {
            plugin.getLogger().info("[Textures] Fetching Minecraft version manifest...");
            String manifest = fetch("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json", 30_000);
            if (manifest == null) { plugin.getLogger().warning("[Textures] Could not fetch version manifest."); return; }

            Matcher latestMatch = Pattern.compile("\"latest\"\\s*:\\s*\\{[^}]*\"release\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(manifest);
            if (!latestMatch.find()) { plugin.getLogger().warning("[Textures] Could not locate latest release in manifest."); return; }
            String version = latestMatch.group(1);

            File targetFile = new File(cacheDir, "client_" + version + ".jar");
            if (targetFile.exists()) {
                JarFile jf = openJar(targetFile);
                if (jf != null) {
                    probeJarStructure(jf);
                    if (hasTextures) {
                        clientJar = jf;
                        plugin.getLogger().info("[Textures] Loaded cached client JAR for latest release " + version);
                        return;
                    }
                    try { jf.close(); } catch (Exception ignored) {}
                }
            }

            Pattern vp = Pattern.compile("\"id\"\\s*:\\s*\"" + Pattern.quote(version) + "\".*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher vm = vp.matcher(manifest);
            if (!vm.find()) { plugin.getLogger().warning("[Textures] Version '" + version + "' not found in manifest."); return; }
            String versionUrl = vm.group(1);

            plugin.getLogger().info("[Textures] Fetching version metadata for " + version + "...");
            String versionJson = fetch(versionUrl, 30_000);
            if (versionJson == null) { plugin.getLogger().warning("[Textures] Could not fetch version JSON."); return; }

            int downloadsIdx = versionJson.indexOf("\"downloads\"");
            if (downloadsIdx < 0) { plugin.getLogger().warning("[Textures] 'downloads' not found in version JSON."); return; }
            int clientIdx = versionJson.indexOf("\"client\"", downloadsIdx);
            if (clientIdx < 0) { plugin.getLogger().warning("[Textures] 'client' not found in version JSON."); return; }
            Matcher um = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(versionJson.substring(clientIdx));
            if (!um.find()) { plugin.getLogger().warning("[Textures] Client JAR URL not found."); return; }
            String clientUrl = um.group(1);

            plugin.getLogger().info("[Textures] Downloading Minecraft " + version + " client JAR (~25 MB)...");
            File tmp = new File(cacheDir, targetFile.getName() + ".tmp");
            tmp.delete();

            URLConnection con = URI.create(clientUrl).toURL().openConnection();
            con.setConnectTimeout(15_000);
            con.setReadTimeout(120_000);
            try (InputStream in = con.getInputStream();
                 java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }

            if (!tmp.renameTo(targetFile)) {
                java.nio.file.Files.copy(tmp.toPath(), targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }

            JarFile jar = new JarFile(targetFile);
            probeJarStructure(jar);
            if (hasTextures) {
                clientJar = jar;
                plugin.getLogger().info("[Textures] Client JAR for " + version + " downloaded — item textures available.");
            } else {
                plugin.getLogger().warning("[Textures] Downloaded JAR but no textures found.");
                try { jar.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Textures] Client JAR download failed: " + e.getMessage());
        }
    }

    private static String fetch(String urlStr, int timeoutMs) {
        try {
            URLConnection con = URI.create(urlStr).toURL().openConnection();
            con.setConnectTimeout(timeoutMs);
            con.setReadTimeout(timeoutMs);
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) baos.write(buf, 0, n);
                return baos.toString("UTF-8");
            }
        } catch (Exception e) { return null; }
    }

    public static byte[] get(Material material) {
        String key = material.name().toLowerCase();
        byte[] cached = BYTES_CACHE.get(key);
        if (cached != null) return cached;
        if (MISSES.contains(key)) return null;

        BufferedImage img = renderToImage(material);
        if (img == null) { MISSES.add(key); return null; }
        byte[] bytes = toPng(img);
        if (bytes == null) { MISSES.add(key); return null; }
        BYTES_CACHE.put(key, bytes);
        return bytes;
    }

    public static byte[] getFace(Material material, String face) {
        String key = material.name().toLowerCase() + "-" + face;
        byte[] cached = BYTES_CACHE.get(key);
        if (cached != null) return cached;
        if (MISSES.contains(key)) return null;

        BufferedImage img = renderFace(material, face);
        if (img == null) { MISSES.add(key); return null; }
        byte[] bytes = toPng(img);
        if (bytes == null) { MISSES.add(key); return null; }
        BYTES_CACHE.put(key, bytes);
        return bytes;
    }

    public static BufferedImage getImage(Material material) {
        return renderToImage(material);
    }

    private static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    private static BufferedImage renderToImage(Material material) {
        if (!hasTextures) return null;
        String name = material.name().toLowerCase();

        BufferedImage itemTexture = loadTexture("assets/minecraft/textures/item/" + name + ".png");
        if (itemTexture != null) {
            BufferedImage base = applyTints(name, itemTexture);
            return upscale(base, 640);
        }
        return renderBlockIsometric(name);
    }

    public static BufferedImage renderFace(Material material, String face) {
        if (!hasTextures) return null;
        String name = material.name().toLowerCase();
        String texName;
        switch (face) {
            case "top":    texName = name + "_top"; break;
            case "bottom": texName = name + "_bottom"; break;
            case "front":  texName = name + "_front"; break;
            case "side":   texName = name + "_side"; break;
            default:       texName = name;
        }

        BufferedImage texture = loadTexture("assets/minecraft/textures/block/" + texName + ".png");
        if (texture == null) texture = loadTexture("assets/minecraft/textures/block/" + name + "_side.png");
        if (texture == null) texture = loadTexture("assets/minecraft/textures/block/" + name + ".png");
        if (texture == null) return null;

        texture = getFirstFrame(texture);
        if (name.equals("grass_block") && face.equals("top")) {
            texture = applyTints("grass_block_top", texture);
        } else if (name.contains("leaves")) {
            texture = applyTints(name, texture);
        }
        return upscale(texture, 640);
    }

    private static BufferedImage upscale(BufferedImage src, int res) {
        BufferedImage up = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = up.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, res, res, null);
        g.dispose();
        return up;
    }

    private static BufferedImage loadTexture(String path) {
        BufferedImage hit = TEXTURE_CACHE.get(path);
        if (hit != null) return hit;
        JarFile jar = clientJar;
        if (jar == null) return null;

        String fullPath = jarAssetPrefix + path;
        synchronized (JAR_LOCK) {
            hit = TEXTURE_CACHE.get(path);
            if (hit != null) return hit;
            JarEntry entry = jar.getJarEntry(fullPath);
            if (entry == null) return null;
            try (InputStream is = jar.getInputStream(entry)) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) TEXTURE_CACHE.put(path, img);
                return img;
            } catch (Exception e) { return null; }
        }
    }

    private static BufferedImage getFirstFrame(BufferedImage img) {
        if (img == null) return null;
        if (img.getHeight() > img.getWidth()) {
            return img.getSubimage(0, 0, img.getWidth(), img.getWidth());
        }
        return img;
    }

    private static BufferedImage applyTints(String textureName, BufferedImage base) {
        Color tint;
        if (textureName.endsWith("grass_block_top")) tint = new Color(0x79c05a);
        else if (textureName.contains("leaves"))     tint = new Color(0x507a32);
        else if (textureName.contains("potion") && !textureName.contains("empty")) tint = new Color(0x385dc6);
        else return base;

        BufferedImage tinted = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tinted.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(tint);
        g.fillRect(0, 0, base.getWidth(), base.getHeight());
        g.dispose();
        return tinted;
    }

    private static BufferedImage renderBlockIsometric(String name) {
        BufferedImage top = loadTexture("assets/minecraft/textures/block/" + name + "_top.png");
        if (top == null) top = loadTexture("assets/minecraft/textures/block/" + name + ".png");
        BufferedImage front = loadTexture("assets/minecraft/textures/block/" + name + "_front.png");
        if (front == null) front = loadTexture("assets/minecraft/textures/block/" + name + "_side.png");
        if (front == null) front = loadTexture("assets/minecraft/textures/block/" + name + ".png");
        BufferedImage side = loadTexture("assets/minecraft/textures/block/" + name + "_side.png");
        if (side == null) side = loadTexture("assets/minecraft/textures/block/" + name + ".png");

        if (top == null || front == null || side == null) return null;

        top = getFirstFrame(top);
        front = getFirstFrame(front);
        side = getFirstFrame(side);

        if (name.equals("grass_block")) {
            top = applyTints("grass_block_top", top);
        } else if (name.contains("leaves")) {
            top = applyTints(name, top);
            front = applyTints(name, front);
            side = applyTints(name, side);
        }

        int S = 16;
        double face = 16.0 * S;
        int res = (int) (face * 2.5);
        double shear = 0.6;

        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);

        int faceI = (int) face;
        BufferedImage topUp   = upscaleSharply(top, faceI);
        BufferedImage frontUp = upscaleSharply(front, faceI);
        BufferedImage sideUp  = upscaleSharply(side, faceI);

        double topOffset = face * 0.15;
        AffineTransform topTx   = new AffineTransform(1.0,  shear, -1.0, shear, face, topOffset);
        AffineTransform leftTx  = new AffineTransform(1.0,  shear,  0.0, 1.0,   0.0,  face * shear + topOffset);
        AffineTransform rightTx = new AffineTransform(1.0, -shear,  0.0, 1.0,   face, face * (shear * 2) + topOffset);

        AffineTransform origTx = g.getTransform();

        g.drawImage(frontUp, leftTx, null);
        g.setTransform(leftTx);
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(0, 0, frontUp.getWidth(), frontUp.getHeight());
        g.setTransform(origTx);

        g.drawImage(sideUp, rightTx, null);
        g.setTransform(rightTx);
        g.setColor(new Color(0, 0, 0, 100));
        g.fillRect(0, 0, sideUp.getWidth(), sideUp.getHeight());
        g.setTransform(origTx);

        g.drawImage(topUp, topTx, null);
        g.dispose();
        return img;
    }

    private static BufferedImage upscaleSharply(BufferedImage src, int size) {
        BufferedImage up = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = up.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return up;
    }
}
