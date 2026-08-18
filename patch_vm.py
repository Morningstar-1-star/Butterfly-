import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

content = re.sub(r'val ytResults = try \{.*?\}\n\s+\}', 'val ytResults = emptyList<VideoItem>()', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
    f.write(content)
