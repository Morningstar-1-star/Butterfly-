import re
from yt_dlp.extractor.common import InfoExtractor

class MissAVIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?missav\.(?:com|ai|live|ws)/(?:[a-z]{2}/)?(?P<id>[a-zA-Z0-9\-]+)'
    _TESTS = [{
        'url': 'https://missav.com/en/ssis-001',
        'info_dict': {
            'id': 'ssis-001',
            'ext': 'mp4',
            'title': 'MissAV Video',
        },
    }]

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)

        title = self._og_search_title(webpage, default=video_id)
        thumbnail = self._og_search_thumbnail(webpage, default=None)

        m3u8_url = self._search_regex(
            r"source\s*:\s*['\"]([^'\"]+\.m3u8[^'\"]*)['\"]",
            webpage, 'm3u8 url', default=None)

        if not m3u8_url:
            m3u8_url = self._search_regex(
                r"['\"](https?://[^\s'\"]+\.m3u8[^\s'\"]*)['\"]",
                webpage, 'm3u8 fallback', default=None)

        formats = []
        if m3u8_url:
            formats = self._extract_m3u8_formats(
                m3u8_url, video_id, 'mp4', entry_protocol='m3u8_native', fatal=False)

        return {
            'id': video_id,
            'title': title,
            'formats': formats,
            'thumbnail': thumbnail,
        }
