import re
from yt_dlp.extractor.common import InfoExtractor

class CoomerIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?coomer\.(?:su|party)/[a-zA-Z0-9_\-]+/user/[^/]+/post/(?P<id>[a-zA-Z0-9]+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)
        
        title = self._og_search_title(webpage, default=video_id)
        
        formats = []
        for mobj in re.finditer(r'<a\s+[^>]*href="([^"]+\.(?:mp4|m4v|mov|webm))"[^>]*>', webpage):
            video_url = mobj.group(1)
            if video_url.startswith('/'):
                video_url = 'https://coomer.su' + video_url
            formats.append({
                'url': video_url,
                'ext': 'mp4',
            })

        return {
            'id': video_id,
            'title': title,
            'formats': formats,
        }
