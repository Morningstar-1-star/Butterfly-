with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "val epornerDeferred" in line:
        new_lines.append("            val epornerDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
    if "val apijavDeferred" in line:
        new_lines.append("            val apijavDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
    if "val torrentDeferred" in line:
        new_lines.append("            val torrentDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
        
    if skip and "val liveHeroes =" in line:
        skip = False
        
    # Also I need to handle when `skip` should end. It ends when we see the NEXT `val xyzDeferred =` or `val liveHeroes =`
    # Let's do it differently.
