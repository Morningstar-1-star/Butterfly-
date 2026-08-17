import re
from yt_dlp.extractor.common import InfoExtractor

class UncensoredJavIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?uncensoredjav\.(?:com|tech|tv)/(?P<id>[a-zA-Z0-9\-]+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)
        
        title = self._og_search_title(webpage, default=video_id)
        thumbnail = self._og_search_thumbnail(webpage, default=None)

        m3u8_url = self._search_regex(
            r'file:\s*["\']([^"\']+\.m3u8[^"\']*)["\']', webpage, 'm3u8 url', default=None)

        formats = []
        if m3u8_url:
            formats = self._extract_m3u8_formats(m3u8_url, video_id, 'mp4', entry_protocol='m3u8_native', fatal=False)

        return {
            'id': video_id,
            'title': title,
            'thumbnail': thumbnail,
            'formats': formats,
        }
