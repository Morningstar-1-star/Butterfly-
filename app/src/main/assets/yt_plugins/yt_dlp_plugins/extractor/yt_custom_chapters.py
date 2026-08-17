import re
from yt_dlp.extractor.common import InfoExtractor

class YtCustomChaptersIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?youtube\.com/watch\?v=(?P<id>[a-zA-Z0-9_\-]+)&chapters=custom'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        return {
            'id': video_id,
            'title': f'Custom Chapters for {video_id}',
            'formats': [],
        }
