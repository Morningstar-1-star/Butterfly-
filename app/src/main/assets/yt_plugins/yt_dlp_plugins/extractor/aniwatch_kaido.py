import re
from yt_dlp.extractor.common import InfoExtractor

class AniwatchKaidoIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?(?:aniwatchtv\.to|kaido\.to)/watch/[a-zA-Z0-9\-]+-(?P<id>\d+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)
        
        title = self._og_search_title(webpage, default=video_id)
        thumbnail = self._og_search_thumbnail(webpage, default=None)

        return {
            'id': video_id,
            'title': title,
            'thumbnail': thumbnail,
            'formats': [],
        }
