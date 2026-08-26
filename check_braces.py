import sys

def check(file):
    with open(file, 'r') as f:
        content = f.read()
        
    stack = []
    for i, char in enumerate(content):
        if char == '{':
            stack.append(i)
        elif char == '}':
            if stack:
                stack.pop()
            else:
                print(f"Extra closing brace at {i}")
                return
    
    if stack:
        print(f"Missing {len(stack)} closing braces. Last opened at {stack[-1]}")
    else:
        print("Braces match")

check("app/src/main/java/com/example/ui/player/GlobalPlayerManager.kt")
