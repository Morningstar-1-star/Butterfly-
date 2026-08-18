import re

with open('app/src/main/java/com/example/util/ReactionHelper.kt', 'r') as f:
    content = f.read()

content = re.sub(r'for \(q in queries\) \{.*?\n        \}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/util/ReactionHelper.kt', 'w') as f:
    f.write(content)
