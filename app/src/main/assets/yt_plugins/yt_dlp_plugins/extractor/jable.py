import re
from yt_dlp.extractor.common import InfoExtractor
from yt_dlp.utils import (
    clean_html,
    get_element_by_class,
    urljoin,
)

class JableIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?jable\.tv/videos/(?P<id>[a-zA-Z0-9\-]+)'
    _TESTS = [{
        'url': 'https://jable.tv/videos/fs1-123/',
        'info_dict': {
            'id': 'fs1-123',
            'ext': 'mp4',
            'title': 'Jable Video',
        },
    }]

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)

        title = self._html_search_regex(
            r'<meta\s+property="og:title"\s+content="([^"]+)"', webpage, 'title', default=None)
        if not title:
            title = self._og_search_title(webpage, default=video_id)

        hls_url = self._search_regex(
            r"var\s+hlsUrl\s*=\047([^\047]+)\047", webpage, 'm3u8 url', default=None)
        if not hls_url:
            hls_url = self._search_regex(
                r'https?://[^\s"\047]+\.m3u8', webpage, 'm3u8 url fallback', default=None)

        formats = []
        if hls_url:
            formats = self._extract_m3u8_formats(hls_url, video_id, 'mp4', entry_protocol='m3u8_native')

        thumbnail = self._og_search_thumbnail(webpage, default=None)

        return {
            'id': video_id,
            'title': title,
            'formats': formats,
            'thumbnail': thumbnail,
        }
