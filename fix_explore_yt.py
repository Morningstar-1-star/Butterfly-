with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "val ytDeferred = async {" in line:
        new_lines.append("            val ytDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
    if "val musicDeferred = async {" in line:
        new_lines.append("            val musicDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
    if "val shortsDeferred = async {" in line:
        new_lines.append("            val shortsDeferred = kotlinx.coroutines.async { emptyList<com.example.model.VideoItem>() }\n")
        skip = True
        continue
    if "val dailymotionDeferred = async {" in line:
        if skip:
            skip = False
            
    if skip and ("val dailymotionDeferred" in line or "val musicDeferred" in line or "val shortsDeferred" in line):
        skip = False

    if not skip:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'w') as f:
    f.writelines(new_lines)
