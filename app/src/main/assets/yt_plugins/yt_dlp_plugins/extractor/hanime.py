import json
from yt_dlp.extractor.common import InfoExtractor

class HanimeTVIE(InfoExtractor):
    _VALID_URL = r'https?://(?:www\.)?hanime\.tv/hentai-videos/(?P<id>[a-zA-Z0-9\-]+)'

    def _real_extract(self, url):
        video_id = self._match_id(url)
        api_url = f'https://hanime.tv/api/v8/video?id={video_id}'
        
        data = self._download_json(api_url, video_id)
        hentai_video = data.get('hentai_video', {})
        title = hentai_video.get('name', video_id)
        thumbnail = hentai_video.get('poster_url')

        formats = []
        videos_manifest = data.get('videos_manifest', {})
        for server in videos_manifest.get('servers', []):
            for stream in server.get('streams', []):
                stream_url = stream.get('url')
                if stream_url:
                    formats.append({
                        'url': stream_url,
                        'height': stream.get('height'),
                        'ext': 'mp4',
                        'format_id': f"{server.get('name')}-{stream.get('height')}p",
                    })

        return {
            'id': video_id,
            'title': title,
            'thumbnail': thumbnail,
            'formats': formats,
        }
