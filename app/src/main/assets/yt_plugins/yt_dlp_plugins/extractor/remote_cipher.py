from __future__ import annotations

import collections
import functools
import itertools
import json
import os
import typing
import urllib.parse

from yt_dlp.extractor.youtube.jsc.provider import (
    JsChallengeProvider,
    JsChallengeProviderError,
    JsChallengeProviderResponse,
    JsChallengeRequest,
    JsChallengeResponse,
    JsChallengeType,
    NChallengeOutput,
    SigChallengeOutput,
    register_provider,
)
from yt_dlp.networking import Request
from yt_dlp.networking.exceptions import HTTPError
from yt_dlp.utils import ExtractorError


@register_provider
class RemoteCipherJCP(JsChallengeProvider):
    PROVIDER_VERSION = '0.0.1'
    PROVIDER_NAME = 'remotecipher'
    REPO_URL = 'https://github.com/coletdjnz/yt-dlp-remote-cipher'
    BUG_REPORT_LOCATION = f'{REPO_URL}/issues'
    DEFAULT_SERVER_BASE_URL = 'http://127.0.0.1:8001'
    _SUPPORTED_TYPES = [JsChallengeType.N, JsChallengeType.SIG]

    def is_available(self) -> bool:
        return True

    @functools.cached_property
    def server_base_url(self) -> str:
        base_url = self._configuration_arg('base_url', default=[None])[0]
        if base_url:
            self.logger.trace(f'Using provided server base url: {base_url}')
            return base_url
        self.logger.debug(f'Using default server base url {self.DEFAULT_SERVER_BASE_URL}')
        return self.DEFAULT_SERVER_BASE_URL

    @functools.cached_property
    def request_timeout(self) -> float | None:
        timeout = self._configuration_arg('timeout', default=[None])[0]
        if timeout is not None:
            self.logger.debug(f'Using provided request timeout: {timeout}')
            return float(timeout)
        return None

    @functools.cached_property
    def api_key(self) -> str | None:
        key = self._configuration_arg('api_key', default=[None])[0]
        if not key:
            key = os.environ.get('REMOTE_CIPHER_API_KEY')
        if key:
            self.logger.trace('Using provided API key for authentication')
            return key
        return None

    def _call_api(self, player_url: str, sig_value: str | None, n_value: str | None) -> dict:
        payload = {
            'player_url': player_url,
            'encrypted_signature': sig_value,
            'n_param': n_value,
        }
        try:
            response = self.ie._download_json(
                Request(
                    url=urllib.parse.urljoin(self.server_base_url, '/decrypt_signature'),
                    data=json.dumps(payload).encode(),
                    headers={
                        'Content-Type': 'application/json',
                        **({'Authorization': f'{self.api_key}'} if self.api_key else {}),
                        'User-Agent': f'yt-dlp Remote Cipher JCP/{self.PROVIDER_VERSION}',
                    },
                    extensions={'timeout': self.request_timeout},
                ),
                note='Solving JS challenges via remote cipher server',
                errnote='Failed to solve JS challenges via remote cipher server',
                video_id=None,
                fatal=True,
            )
        except ExtractorError as e:
            if isinstance(e.cause, HTTPError):
                if e.cause.status == 401:
                    if self.api_key is None:
                        message = (
                            'No API key provided; pass one with --extractor-args "youtubejsc-remotecipher:api_key=API_KEY" '
                            'or the REMOTE_CIPHER_API_KEY environment variable. '
                            f'For more details, refer to  {self.REPO_URL}')
                    else:
                        message = 'The provided API key is probably invalid'
                    raise JsChallengeProviderError(
                        'HTTP Error 401: Authentication failed with remote cipher server. ' + message) from e
                raise JsChallengeProviderError(f'HTTP error from remote cipher server: {e}', expected=True) from e

            raise JsChallengeProviderError(f'HTTP request to remote cipher server failed: {e}', expected=True) from e

        if not isinstance(response, dict):
            raise JsChallengeProviderError('Invalid response from remote solver: expected JSON object')

        error = response.get('error')
        if error:
            raise JsChallengeProviderError(f'Remote cipher server returned an error: {error}')

        return response

    def _flatten_challenges(self, requests: list[JsChallengeRequest]) -> tuple[list[tuple[JsChallengeRequest, str]], list[tuple[JsChallengeRequest, str]]]:
        flat_sig_items: list[tuple[JsChallengeRequest, str]] = []
        flat_n_items: list[tuple[JsChallengeRequest, str]] = []

        for req in requests:
            if req.type is JsChallengeType.SIG:
                for ch in req.input.challenges or []:
                    flat_sig_items.append((req, ch))
            elif req.type is JsChallengeType.N:
                for ch in req.input.challenges or []:
                    flat_n_items.append((req, ch))

        return flat_sig_items, flat_n_items

    def _prepare_results_map(self, requests: list[JsChallengeRequest]) -> dict[int, dict]:
        results_map: dict[int, dict] = {}
        for req in requests:
            results_map[id(req)] = {'request': req, 'n': {}, 'sig': {}, 'error': None}
        return results_map

    def _yield_responses_from_map(self, results_map: dict[int, dict]) -> typing.Generator[JsChallengeProviderResponse, None, None]:
        for entry in results_map.values():
            req = entry['request']
            if req.type is JsChallengeType.N:
                if entry['n']:
                    yield JsChallengeProviderResponse(request=req, response=JsChallengeResponse(JsChallengeType.N, NChallengeOutput(results=entry['n'])))
            elif req.type is JsChallengeType.SIG:
                if entry['sig']:
                    yield JsChallengeProviderResponse(request=req, response=JsChallengeResponse(JsChallengeType.SIG, SigChallengeOutput(results=entry['sig'])))

    def _real_bulk_solve(self, requests: list[JsChallengeRequest]) -> typing.Generator[JsChallengeProviderResponse, None, None]:
        # Group requests by player_url
        grouped: dict[str, list[JsChallengeRequest]] = collections.defaultdict(list)
        for request in requests:
            grouped[request.input.player_url].append(request)

        for player_url, grouped_requests in grouped.items():
            # remote cipher server only supports one of each type of challenge at a time (sig + n)
            # flatten the challenge requests so we send sig+n in one go using minimal requests
            flat_sig_items, flat_n_items = self._flatten_challenges(grouped_requests)
            results_map = self._prepare_results_map(grouped_requests)

            sig_iterable = flat_sig_items or [(None, None)]
            n_iterable = flat_n_items or [(None, None)]

            for (sig_req, sig_val), (n_req, n_val) in itertools.zip_longest(sig_iterable, n_iterable, fillvalue=(None, None)):
                if sig_req is None and n_req is None:
                    continue
                response = self._call_api(player_url, sig_val, n_val)
                solved_sig = response.get('decrypted_signature')
                solved_n = response.get('decrypted_n_sig')

                if solved_sig is not None and sig_req is not None and sig_val is not None:
                    results_map[id(sig_req)]['sig'][sig_val] = solved_sig

                if solved_n is not None and n_req is not None and n_val is not None:
                    results_map[id(n_req)]['n'][n_val] = solved_n

            yield from self._yield_responses_from_map(results_map)
