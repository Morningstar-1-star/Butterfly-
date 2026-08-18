import re

with open('app/src/main/java/com/example/plugin/manager/SourcePipelineEngine.kt', 'r') as f:
    content = f.read()

content = re.sub(r'// 1\.8 FIRST-CLASS YtDlpResolver Extraction.*?// 2\. PARALLEL CLOUD SEARCH ENGINE DELEGATION', '// 2. PARALLEL CLOUD SEARCH ENGINE DELEGATION', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/plugin/manager/SourcePipelineEngine.kt', 'w') as f:
    f.write(content)
