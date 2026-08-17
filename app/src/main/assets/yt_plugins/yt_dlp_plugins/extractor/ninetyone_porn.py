import re
from yt_dlp.extractor.common import InfoExtractor

class NinetyOnePornIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?91porn\.com/v\.php\?category=[^&]*&viewkey=(?P<id>[a-zA-Z0-9]+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)

        title = self._html_search_regex(r'<h4 class="login_register_header"[^>]*>([^<]+)</h4>', webpage, 'title', default=video_id).strip()

        video_url = self._search_regex(
            r'<source src="([^"]+)" type=\'video/mp4\'>',
            webpage, 'video url', default=None)

        formats = []
        if video_url:
            formats.append({
                'url': video_url,
                'ext': 'mp4',
                'format_id': 'http-sd',
            })

        return {
            'id': video_id,
            'title': title,
            'formats': formats,
        }
