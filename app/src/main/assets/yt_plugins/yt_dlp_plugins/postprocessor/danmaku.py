import os
import re
import json
from yt_dlp.postprocessor.common import PostProcessor

class DanmakuPP(PostProcessor):
    def __init__(self, downloader=None):
        super().__init__(downloader)

    def run(self, info):
        self.to_screen('Processing Danmaku comments/subtitles...')
        comments = info.get('comments') or []
        if not comments and 'danmaku' in info:
            comments = info.get('danmaku')
        
        if comments:
            filename = self._get_output_filename(info)
            xml_filename = os.path.splitext(filename)[0] + '.xml'
            try:
                with open(xml_filename, 'w', encoding='utf-8') as f:
                    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<i>')
                    for c in comments:
                        text = c.get('text', '')
                        time = c.get('timestamp', 0)
                        f.write(f'<d p="{time},1,25,16777215,0,0,0,0">{text}</d>\n')
                    f.write('</i>')
                self.to_screen(f'Saved Danmaku XML to {xml_filename}')
            except Exception as e:
                self.to_screen(f'Failed to write Danmaku XML: {e}')
        return [], info

    def _get_output_filename(self, info):
        if '_filename' in info:
            return info['_filename']
        return 'danmaku_output.mp4'
