package com.cell4.common.util;

import com.cell4.Cell4;
import com.cell4.common.item.IInfinityCell;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads config/Cell4/config.json and manages the runtime resource pack used for
 * custom cell textures.
 * <p>
 * The resource pack owns the texture files directly (no copying / hard links):
 * <pre>
 * config/Cell4/
 *   config.json
 *   .pack/                                   (registered as a built-in resource pack)
 *     pack.mcmeta                            (generated)
 *     assets/cell4/textures/item/            (your textures: &lt;name&gt;.png + optional &lt;name&gt;.png.mcmeta)
 *     assets/cell4/models/item/...           (generated model files)
 * </pre>
 * Put &lt;name&gt;.png directly into the textures/item folder, set the cell texture name
 * to &lt;name&gt; in the Cell Configurator, then press F3+T.
 */
public final class Cell4Config {

    public boolean enabled = true;
    public int maxTextureSets = 64;
    public String textureFolder = "texture";
    public String imageFile = "item.png";
    public Portable portable = new Portable();

    public static class Portable {
        public long defaultMaxPower = 100000L;
        public long defaultCostPerItem = 20L;
        public double chargeRate = 8.0;
        public double idleDrain = 0.0;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Cell4Config INSTANCE = new Cell4Config();
    private static long configLastModified = -1L;
    /** 文件 stat 节流：get() 在渲染/网络热路径被频繁调用，最多每 1000ms 检查一次修改时间。 */
    private static final long STAT_CHECK_INTERVAL_MS = 1000L;
    private static long lastStatCheck = 0L;
    private static List<String> textureSets = List.of();
    private static Path rootDir;
    private static Path textureDir;
    private static Path packDir;

    private Cell4Config() {}

    /** 配置被外部修改（F3+T 或手动编辑）后自动重载。 */
    public static Cell4Config get() {
        if (rootDir != null) {
            long now = System.currentTimeMillis();
            if (now - lastStatCheck < STAT_CHECK_INTERVAL_MS) {
                return INSTANCE;
            }
            lastStatCheck = now;
            Path configFile = rootDir.resolve("config.json");
            try {
                long mod = Files.getLastModifiedTime(configFile).toMillis();
                if (mod != configLastModified) {
                    configLastModified = mod;
                    try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                        Cell4Config loaded = GSON.fromJson(reader, Cell4Config.class);
                        if (loaded != null && loaded.portable != null) {
                            INSTANCE = loaded;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return INSTANCE;
    }
    public static List<String> getTextureSets() { return textureSets; }
    public static Path getPackDir() { return packDir; }
    public static Path getRootDir() { return rootDir; }
    public static Path getTextureDir() { return textureDir; }

    public static void init() {
        try {
            rootDir = FMLPaths.CONFIGDIR.get().resolve("Cell4");
            Files.createDirectories(rootDir);
            Path configFile = rootDir.resolve("config.json");
            if (Files.exists(configFile)) {
                try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    Cell4Config loaded = GSON.fromJson(reader, Cell4Config.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            }
            if (INSTANCE.portable == null) INSTANCE.portable = new Portable();
            // Always rewrite the config so newly added defaults are visible to users.
            try (var writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }

            packDir = rootDir.resolve(".pack");
            // 材质直接放在资源包内，避免复制/硬链接，也避免重载时生成贴图与 atlas 扫描竞争。
            textureDir = packDir.resolve("assets/cell4/textures/item");
            Files.createDirectories(textureDir);
            migrateLegacyTextures();
            createExampleTexture();

            textureSets = scanTextureSets();
            generatePack(true);
            Cell4.LOGGER.info("Cell4: found {} custom texture set(s) in {}", textureSets.size(), textureDir);
        } catch (Throwable t) {
            Cell4.LOGGER.warn("Cell4: failed to initialise custom textures", t);
            textureSets = List.of();
            packDir = rootDir != null ? rootDir.resolve(".pack") : null;
            textureDir = packDir != null ? packDir.resolve("assets/cell4/textures/item") : null;
        }
    }

    /** F3+T 重载资源包时重新扫描材质；只有材质集合变化时才重写模型文件。 */
    public static void refresh() {
        try {
            if (textureDir == null || packDir == null) return;
            List<String> scanned = scanTextureSets();
            // 贴图和 mcmeta 本来就在包内，重载后 atlas 会重新读取；模型未变时无需重写。
            if (scanned.equals(textureSets) && Files.isRegularFile(packDir.resolve("pack.mcmeta"))) {
                return;
            }
            textureSets = scanned;
            generatePack(false);
        } catch (Throwable t) {
            Cell4.LOGGER.warn("Cell4: failed to refresh custom textures", t);
        }
    }

    /** 把旧版 config/Cell4/texture/ 下的文件移动到资源包内（只移动，不复制）。 */
    private static void migrateLegacyTextures() {
        if (rootDir == null || textureDir == null) return;
        Path legacy = rootDir.resolve("texture");
        if (!Files.isDirectory(legacy)) return;
        try (Stream<Path> files = Files.list(legacy)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String n = p.getFileName().toString().toLowerCase();
                if (n.endsWith(".png") || n.endsWith(".png.mcmeta")) {
                    moveIfAbsent(p, textureDir.resolve(p.getFileName().toString()));
                }
            });
        } catch (IOException ignored) {
        }
        try (Stream<Path> dirs = Files.list(legacy)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                Path img = dir.resolve("item.png");
                Path meta = dir.resolve("item.png.mcmeta");
                if (Files.isRegularFile(img)) moveIfAbsent(img, textureDir.resolve(name + ".png"));
                if (Files.isRegularFile(meta)) moveIfAbsent(meta, textureDir.resolve(name + ".png.mcmeta"));
            });
        } catch (IOException ignored) {
        }
    }

    private static void moveIfAbsent(Path source, Path target) {
        try {
            if (!Files.exists(target)) {
                Files.move(source, target);
            }
        } catch (IOException e) {
            Cell4.LOGGER.warn("Cell4: failed to move {} to {}", source, target, e);
        }
    }

    /** 0 = default texture, 1..N = configured texture set. */
    public static int getTextureIndex(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof IInfinityCell cell)) return 0;
        return getTextureIndex(cell.getTextureName(stack));
    }

    public static int getTextureIndex(String name) {
        if (name == null || name.isEmpty()) return 0;
        int index = textureSets.indexOf(name);
        return index < 0 ? 0 : index + 1;
    }

    // === Texture scanning ===

    private static List<String> scanTextureSets() {
        List<String> result = new ArrayList<>();
        if (!Files.isDirectory(textureDir)) return result;
        try (Stream<Path> files = Files.list(textureDir)) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".png"))
                    .filter(n -> !n.toLowerCase().endsWith(".png.mcmeta"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .filter(Cell4Config::isValidName)
                    .distinct()
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            Cell4.LOGGER.warn("Cell4: failed to scan {}", textureDir, e);
        }
        if (result.size() > INSTANCE.maxTextureSets) {
            return new ArrayList<>(result.subList(0, Math.max(0, INSTANCE.maxTextureSets)));
        }
        return result;
    }

    /** 资源路径只允许小写字母、数字、下划线、点和短横线。 */
    private static boolean isValidName(String name) {
        return name.matches("[a-z0-9_.-]+");
    }

    private static void createExampleTexture() throws IOException {
        try (Stream<Path> entries = Files.list(textureDir)) {
            if (entries.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))) return;
        }
        Path example = textureDir.resolve("example.png");
        try (InputStream in = Cell4Config.class.getResourceAsStream("/assets/cell4/textures/item/cell_item.png")) {
            if (in != null) {
                Files.copy(in, example, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(textureDir.resolve("README.txt"),
                "Put <name>.png (16x16, or animated with <name>.png.mcmeta next to it) directly in this folder.\n"
                        + "Then set the cell's texture name to <name> in the Cell Configurator.\n"
                        + "Press F3+T in game to reload textures.\n",
                StandardCharsets.UTF_8);
    }

    // === Resource pack generation ===

    private static void generatePack(boolean clear) throws IOException {
        if (!INSTANCE.enabled || packDir == null) return;
        // 贴图就在包内，清理时只删生成的模型，绝不碰贴图文件。
        if (clear) {
            deleteRecursively(packDir.resolve("assets/cell4/models"));
            // 旧版每个材质一个贴图子文件夹，清理掉避免残留。
            deleteRecursively(packDir.resolve("assets/cell4/textures/item/customtex"));
        }
        Files.createDirectories(packDir);
        write(packDir.resolve("pack.mcmeta"),
                "{\n"
                        + "  \"pack\": {\n"
                        + "    \"description\": \"Cell4 Custom Textures\",\n"
                        + "    \"pack_format\": 84,\n"
                        + "    \"min_format\": 84,\n"
                        + "    \"max_format\": 84\n"
                        + "  }\n"
                        + "}\n");

        // Per-texture model files (textures themselves live in textures/item).
        Map<Integer, String> modelPaths = new LinkedHashMap<>();
        for (int i = 0; i < textureSets.size(); i++) {
            int index = i + 1;
            String name = textureSets.get(i);
            String modelName = sanitize(name);
            modelPaths.put(index, modelName);
            write(packDir.resolve("assets/cell4/models/item/customtex/" + modelName + ".json"),
                    "{\n"
                            + "  \"parent\": \"minecraft:item/generated\",\n"
                            + "  \"textures\": {\n"
                            + "    \"layer0\": \"cell4:item/" + name + "\"\n"
                            + "  }\n"
                            + "}\n");
        }

        // 26.1 item definitions: select the custom model by the cell4texture value.
        if (!modelPaths.isEmpty()) {
            for (String cell : BASE_TEXTURES.keySet()) {
                StringBuilder json = new StringBuilder();
                json.append("{\n  \"model\": {\n")
                        .append("    \"type\": \"minecraft:select\",\n")
                        .append("    \"property\": \"cell4:cell_texture\",\n")
                        .append("    \"cases\": [\n");
                int n = 0;
                for (int i = 0; i < textureSets.size(); i++) {
                    int index = i + 1;
                    String name = textureSets.get(i);
                    String modelName = modelPaths.get(index);
                    if (n++ > 0) json.append(",\n");
                    json.append("      {\"when\": \"").append(escapeJson(name))
                            .append("\", \"model\": {\"type\": \"minecraft:model\", \"model\": \"cell4:item/customtex/")
                            .append(modelName).append("\"}}");
                }
                json.append("\n    ],\n")
                        .append("    \"fallback\": {\"type\": \"minecraft:model\", \"model\": \"cell4:item/")
                        .append(cell).append("\"}\n")
                        .append("  }\n}\n");
                write(packDir.resolve("assets/cell4/items/" + cell + ".json"), json.toString());
            }
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Item id -> base texture path (under assets/cell4/textures/). */
    public static final Map<String, String> BASE_TEXTURES = new LinkedHashMap<>();

    static {
        BASE_TEXTURES.put("infinity_item_cell", "cell_item");
        BASE_TEXTURES.put("infinity_tag_cell", "cell_tag");
        BASE_TEXTURES.put("infinity_modid_cell", "cell_modid");
        BASE_TEXTURES.put("portable_infinity_item_cell", "cell_item");
        BASE_TEXTURES.put("portable_infinity_tag_cell", "cell_tag");
        BASE_TEXTURES.put("portable_infinity_modid_cell", "cell_modid");
    }

    public static String sanitize(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
        return s.isEmpty() ? "texture" : s;
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        }
    }
}
