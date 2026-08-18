import re

with open('app/src/main/java/com/example/engine/SearchEngine.kt', 'r') as f:
    content = f.read()

content = content.replace(
"""                when (val result = YouTubeExtractorHelper.searchVideos(cleanQuery)) {
                    is FeedResult.Success -> result.items
                    is FeedResult.Error -> emptyList()
                }""",
"""                val result = YouTubeExtractorHelper.searchVideos(cleanQuery)
                if (result is UrlParseResult.ParsedSearchResults) result.items else emptyList()"""
)

with open('app/src/main/java/com/example/engine/SearchEngine.kt', 'w') as f:
    f.write(content)
