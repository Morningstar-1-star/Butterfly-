import re

with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(r'val torrentDeferred = async \{.*?\n\s+\}', 'val torrentDeferred = async { emptyList<VideoItem>() }', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'w') as f:
    f.write(content)
