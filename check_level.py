import sys

with open('app/src/main/java/com/example/ui/player/GlobalPlayerManager.kt', 'r') as f:
    lines = f.readlines()
    
level = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            level += 1
        elif char == '}':
            level -= 1
            if level == 0:
                print(f"Level reached 0 at line {i+1}: {line.strip()}")
