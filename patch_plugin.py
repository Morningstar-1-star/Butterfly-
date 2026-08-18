import re

with open('app/src/main/java/com/example/plugin/manager/PluginManager.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if any(p in line for p in [
        "OrionProvider", "CometProvider", "ZileanProvider", "MediaFusionProvider", 
        "VidSrcProvider", "ApiJavServerProvider", "ApiJavHentaiProvider", 
        "ApiJavPornProvider", "JavInfoProvider", "EpornerProvider", 
        "TorrentApiMultiProvider", "YouTubeProvider", "VegaMultiProvider", 
        "AutoEmbedProvider", "EztvTorrentProvider", "NyaaAnimeProvider", 
        "TmdbTorrentProvider", "YtsTorrentProvider", "UnifiedTorrentProvider",
        "TorrentioAggregatorProvider"
    ]):
        continue
    new_lines.append(line)

with open('app/src/main/java/com/example/plugin/manager/PluginManager.kt', 'w') as f:
    f.writelines(new_lines)
