import re
from yt_dlp.extractor.common import InfoExtractor

class HiAnimeIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?hianime\.(?:to|nz|mm)/(?:watch|anime)/[a-zA-Z0-9\-]+-(?P<id>\d+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        webpage = self._download_webpage(url, video_id)
        title = self._og_search_title(webpage, default=video_id)
        thumbnail = self._og_search_thumbnail(webpage, default=None)
        
        iframe_url = self._search_regex(
            r'<iframe\s+[^>]*src="([^"]+)"', webpage, 'iframe url', default=None)
        
        formats = []
        if iframe_url:
            if iframe_url.startswith('//'):
                iframe_url = 'https:' + iframe_url
            iframe_page = self._download_webpage(iframe_url, video_id, note='Downloading iframe')
            m3u8_url = self._search_regex(
                r'file:\s*["\']([^"\']+\.m3u8[^"\']*)["\']', iframe_page, 'm3u8 url', default=None)
            if m3u8_url:
                formats = self._extract_m3u8_formats(m3u8_url, video_id, 'mp4', entry_protocol='m3u8_native', fatal=False)

        return {
            'id': video_id,
            'title': title,
            'thumbnail': thumbnail,
            'formats': formats,
        }
