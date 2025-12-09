# Donut Auto-Auction Mod

A Fabric mod for DonutSMP that enables automatic auctioning with a single keybind. Press **Ctrl+Alt+LMB** on any item to automatically auction it with intelligent pricing based on current auction house data.

## Features

### Core Functionality
- **Ctrl+Alt+LMB Keybinding**: Quick-auction any item in your inventory or on the ground
- **Automatic Pricing**: Queries DonutSMP Auction House API to suggest competitive prices (default: lowest BIN - 5%)
- **Auto-Confirmation**: Automatically confirms auction after price entry
- **Item Analysis**: Extracts material, enchantments, and NBT data for accurate auction matching
- **API Integration**: Real-time auction data from DonutSMP API

### Safety Features
- **Humanized Delays**: Random 1-3 tick delays between actions to avoid anti-cheat detection
- **Server Whitelist**: Only functions on DonutSMP (can be configured)
- **Error Handling**: Toast notifications for failed auctions
- **Session Logging**: Tracks auctions/hour and average profit

### Configuration
- **Keybind Remapping**: Customize Ctrl+Alt+LMB to another combination
- **Price Multiplier**: Adjust pricing (90-110% of lowest BIN)
- **Item Blacklist**: Exclude specific items from auto-auction
- **Cooldown Timer**: Set cooldown between consecutive auctions (5-30 seconds)
- **API Whitelist**: Configure allowed servers

## Installation

### Prerequisites
- Minecraft 1.21.1+
- Fabric Loader 0.16.9+
- Fabric API 0.102.1+

### Setup
1. Download the latest release JAR
2. Place in `.minecraft/mods/` folder
3. Launch Minecraft with Fabric profile
4. Join DonutSMP and run `/api` to authorize the mod
5. Press Ctrl+Alt+LMB on any item to auction

## Building from Source

```bash
git clone https://github.com/Papi99972/donut-auto-auction.git
cd donut-auto-auction
./gradlew build
```

JAR output: `build/libs/donut-auto-auction-1.0.0.jar`

## Configuration File

Config saved to: `.minecraft/config/donut-auto-auction.json`

```json
{
  "enabled": true,
  "keybind": "ctrl+alt+mouse_left",
  "price_multiplier": 0.95,
  "cooldown_seconds": 5,
  "blacklisted_items": [],
  "allowed_servers": ["donutsmp.net"]
}
```

## API Documentation

The mod uses the [DonutSMP Public API](https://api.donutsmp.net):

- **Authentication**: Uses API key from `/api` command
- **Endpoint**: `GET /v1/auction/list?item={hash}`
- **Response**: Returns list of current auctions with lowest prices

## Architecture

### Key Classes
- **DonutAutoAuctionMod**: Main mod entry point
- **KeybindHandler**: Registers and polls Ctrl+Alt+LMB keybinding
- **ItemAnalyzer**: Extracts item metadata (material, enchants, NBT)
- **AuctionAPIClient**: HTTP client for DonutSMP API queries
- **AuctionExecutor**: Handles chat command automation and confirmations
- **ConfigManager**: Loads/saves mod configuration
- **AntiCheatProtection**: Adds human-like delays and randomization

## Technical Details

### Keybind Detection
Uses Fabric ClientTickEvents.END_CLIENT_TICK to poll for Ctrl+Alt+LMB combination via GLFW key codes

### Item Identification
Computes SHA-256 hash of: `<material>:<enchantments>:<custom_name>:<lore>` for auction matching

### Chat Automation
Injects commands via ClientPlayerEntity.sendChatMessage() with 100-300ms randomized delays

### Auction Confirmation
Monitors ScreenEvents.AFTER_INIT to detect confirmation GUI and auto-clicks green glass pane

## Known Limitations

- Requires manual API key setup (run `/api` once)
- Only works on DonutSMP servers (configurable)
- Cannot auction locked items or quest items
- Confirmation GUI must appear within 30 seconds

## Troubleshooting

**Q: Mod not detecting items?**  
A: Ensure you ran `/api` command. API key is saved locally.

**Q: Auctions failing?**  
A: Check if item is auctionable. Some items cannot be listed. Check chat for error messages.

**Q: Getting kicked for macro usage?**  
A: Humanization delays are enabled by default. If still getting flagged, disable the mod.

## Performance

- **Memory**: ~5-10 MB
- **Network**: 1 API call per auction (~50-100ms latency)
- **CPU**: Negligible (only active on keybind press)

## Legal Notice

This mod is designed for legitimate gameplay on DonutSMP. Respect server ToS. The author is not responsible for account actions taken using this mod.

## Contributing

Pull requests welcome! Please ensure:
- Code follows Fabric conventions
- Changes are tested on latest MC 1.21.1
- Documentation is updated

## License

MIT License - See LICENSE file

## Credits

- Built with [Fabric](https://fabricmc.net/)
- Uses [DonutSMP API](https://api.donutsmp.net)
- Inspired by Auction House optimization mods
