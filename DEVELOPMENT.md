# Development Guide: Donut Auto-Auction Mod

This document outlines the complete architecture and implementation specifications for all module components.

## Project Structure

```
src/main/java/net/donutsmp/mod/
├── DonutAutoAuctionMod.java          (Main entry point)
├── KeybindHandler.java               (Keybind polling & detection)
├── ItemAnalyzer.java                 (NBT parsing & item hashing)
├── AuctionAPIClient.java             (HTTP API wrapper)
├── AuctionExecutor.java              (Chat automation & confirmation)
├── ConfigManager.java                (Config file I/O)
└── AntiCheatProtection.java          (Delay randomization)

src/main/resources/
└── fabric.mod.json                   (Mod metadata)
```

## Module Specifications

### 1. KeybindHandler.java

**Purpose**: Detect Ctrl+Alt+LMB keybind and trigger auction execution

**Implementation**:
```java
public class KeybindHandler {
    private final ConfigManager config;
    private long lastExecutionTime = 0;
    
    public KeybindHandler(ConfigManager config) {
        this.config = config;
    }
    
    public void handleKeybind(MinecraftClient client, KeyBinding key, AuctionExecutor executor) {
        if (!Screen.hasControlDown() || !Screen.hasAltDown()) return;
        if (key.wasPressed()) {
            long now = System.currentTimeMillis();
            if (now - lastExecutionTime < config.getCooldownMs()) {
                return;  // Cooldown active
            }
            
            lastExecutionTime = now;
            ItemStack itemStack = getTargetItem(client);
            if (itemStack != null) {
                executor.executeAuction(client.player, itemStack);
            }
        }
    }
    
    private ItemStack getTargetItem(MinecraftClient client) {
        if (client.player.getMainHandStack().isEmpty()) {
            return null;
        }
        return client.player.getMainHandStack();
    }
}
```

**Key Methods**:
- `handleKeybind()`: Polls keybind with cooldown enforcement
- `getTargetItem()`: Returns hovered/held item
- Respects config cooldown via `config.getCooldownMs()`

---

### 2. ItemAnalyzer.java

**Purpose**: Extract item metadata and compute unique hash for API matching

**Implementation**:
```java
public class ItemAnalyzer {
    
    public static String analyzeItem(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        
        // Material
        sb.append(stack.getItem().getName().getString());
        sb.append(":");
        
        // Enchantments
        for (Enchantment ench : stack.getEnchantments().getEnchantments()) {
            sb.append(ench.getName().getString())
              .append(":")
              .append(stack.getEnchantments().getLevel(ench))
              .append(";");
        }
        sb.append(":");
        
        // Custom name
        if (stack.hasCustomName()) {
            sb.append(stack.getName().getString());
        }
        sb.append(":");
        
        // NBT (lore, attributes, etc)
        if (stack.hasNbt()) {
            NbtCompound nbt = stack.getNbt();
            if (nbt.contains("display")) {
                NbtCompound display = nbt.getCompound("display");
                if (display.contains("Lore")) {
                    sb.append(display.get("Lore").toString());
                }
            }
        }
        
        // SHA-256 hash
        return sha256(sb.toString());
    }
    
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

---

### 3. AuctionAPIClient.java

**Purpose**: Query DonutSMP Auction House API for price data

**Implementation**:
```java
public class AuctionAPIClient {
    private static final String API_BASE = "https://api.donutsmp.net/v1";
    private final String apiKey;  // From /api command
    
    public AuctionAPIClient(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public List<AuctionInfo> getLowestBIN(String itemHash) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) 
            new URL(API_BASE + "/auction/list?item=" + itemHash).openConnection();
        
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        
        if (conn.getResponseCode() != 200) {
            throw new Exception("API Error: " + conn.getResponseCode());
        }
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        JsonObject response = JsonParser.parseString(
            reader.lines().collect(Collectors.joining())).getAsJsonObject();
        reader.close();
        
        List<AuctionInfo> auctions = new ArrayList<>();
        for (JsonElement elem : response.getAsJsonArray("auctions")) {
            JsonObject auction = elem.getAsJsonObject();
            auctions.add(new AuctionInfo(
                auction.get("id").getAsString(),
                auction.get("price").getAsLong(),
                auction.get("seller").getAsString()
            ));
        }
        
        return auctions.stream()
            .sorted(Comparator.comparingLong(a -> a.price))
            .limit(10)
            .collect(Collectors.toList());
    }
    
    public static class AuctionInfo {
        public String id;
        public long price;
        public String seller;
        
        public AuctionInfo(String id, long price, String seller) {
            this.id = id;
            this.price = price;
            this.seller = seller;
        }
    }
}
```

---

### 4. AuctionExecutor.java

**Purpose**: Execute auction via chat commands and handle confirmations

**Implementation**:
```java
public class AuctionExecutor {
    private final ConfigManager config;
    private final AntiCheatProtection antiCheat;
    private final AuctionAPIClient apiClient;
    
    public void executeAuction(ClientPlayerEntity player, ItemStack item) {
        try {
            // Analyze item
            String itemHash = ItemAnalyzer.analyzeItem(item);
            
            // Query API
            List<AuctionAPIClient.AuctionInfo> auctions = 
                apiClient.getLowestBIN(itemHash);
            
            if (auctions.isEmpty()) {
                player.sendMessage(Text.literal("No comparable auctions found"), false);
                return;
            }
            
            // Calculate price
            long lowestPrice = auctions.get(0).price;
            long suggestedPrice = (long)(lowestPrice * config.getPriceMultiplier());
            
            // Send command with delay
            antiCheat.delay(100, 300);  // 100-300ms random
            player.sendChatMessage("/ah sell " + suggestedPrice);
            
            // Wait for confirmation GUI
            ConfirmationListener listener = new ConfirmationListener(suggestedPrice);
            ScreenEvents.AFTER_INIT.register(listener);
            
        } catch (Exception e) {
            DonutAutoAuctionMod.LOGGER.error("Auction failed", e);
            player.sendMessage(Text.literal("Auction error: " + e.getMessage()), false);
        }
    }
}
```

---

### 5. ConfigManager.java

**Purpose**: Load/save configuration from `config/donut-auto-auction.json`

**Implementation**:
```java
public class ConfigManager {
    private static final Path CONFIG_PATH = 
        FabricLoader.getInstance().getConfigDir()
        .resolve("donut-auto-auction.json");
    
    private boolean enabled = true;
    private double priceMultiplier = 0.95;
    private long cooldownMs = 5000;
    private List<String> blacklistedItems = new ArrayList<>();
    private List<String> allowedServers = List.of("donutsmp.net");
    
    public void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            saveConfig();
            return;
        }
        
        try {
            String json = Files.readString(CONFIG_PATH);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            
            enabled = obj.get("enabled").getAsBoolean();
            priceMultiplier = obj.get("price_multiplier").getAsDouble();
            cooldownMs = obj.get("cooldown_ms").getAsLong();
            
        } catch (IOException e) {
            DonutAutoAuctionMod.LOGGER.error("Failed to load config", e);
        }
    }
    
    public void saveConfig() {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", enabled);
        obj.addProperty("price_multiplier", priceMultiplier);
        obj.addProperty("cooldown_ms", cooldownMs);
        
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, new GsonBuilder().setPrettyPrinting()
                .create().toJson(obj));
        } catch (IOException e) {
            DonutAutoAuctionMod.LOGGER.error("Failed to save config", e);
        }
    }
    
    public boolean isEnabled() { return enabled; }
    public double getPriceMultiplier() { return priceMultiplier; }
    public long getCooldownMs() { return cooldownMs; }
}
```

---

### 6. AntiCheatProtection.java

**Purpose**: Add humanized delays to evade detection

**Implementation**:
```java
public class AntiCheatProtection {
    private final Random random = new Random();
    
    public void delay(int minMs, int maxMs) {
        int delayMs = minMs + random.nextInt(maxMs - minMs + 1);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void randomizeJitter() {
        // Add ±5% jitter to prices
        if (random.nextBoolean()) {
            // Vary price slightly
        }
    }
}
```

---

## Building & Testing

```bash
# Build
./gradlew build

# Test
./gradlew test

# Generate sources JAR
./gradlew sourcesJar
```

## Dependencies

- Fabric Loader 0.16.9+
- Fabric API 0.102.1+
- Gson 2.10.1 (for JSON parsing)
- Java 21+

## Future Enhancements

- [ ] GUI config screen using Cloth Config
- [ ] Flip profit tracking
- [ ] Blacklist/whitelist manager screen
- [ ] Custom pricing algorithms
- [ ] Enchantment level detection
- [ ] Item comparison with historical prices
- [ ] Batch auction support
