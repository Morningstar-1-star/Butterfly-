import re

with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(r'val epornerDeferred = async \{.*?\n\s+\}', 'val epornerDeferred = async { emptyList<VideoItem>() }', content, flags=re.DOTALL)
content = re.sub(r'val apijavDeferred = async \{.*?\n\s+\}', 'val apijavDeferred = async { emptyList<VideoItem>() }', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/ExploreScreen.kt', 'w') as f:
    f.write(content)
