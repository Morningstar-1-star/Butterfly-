import re
from yt_dlp.extractor.common import InfoExtractor

class PMVHavenIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?pmvhaven\.(?:org|com)/v/(?P<id>[a-zA-Z0-9\-]+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)
        
        title = self._og_search_title(webpage, default=video_id)
        thumbnail = self._og_search_thumbnail(webpage, default=None)

        video_url = self._search_regex(
            r'<source\s+[^>]*src="([^"]+)"', webpage, 'video url', default=None)
        
        formats = []
        if video_url:
            formats.append({
                'url': video_url,
                'ext': 'mp4',
            })

        return {
            'id': video_id,
            'title': title,
            'thumbnail': thumbnail,
            'formats': formats,
        }
