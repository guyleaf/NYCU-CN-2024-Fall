from http import HTTPStatus
import json
import logging
from types import TracebackType
from typing import AsyncGenerator, Optional, Tuple, Type
from urllib.parse import urljoin
import aiohttp

from aiosseclient import aiosseclient


class API:
    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url
        self.auth = aiohttp.BasicAuth(username, password)
        self.logger = logging.getLogger(__name__)

    # TODO: Convert json to dto
    async def listen_events(self) -> AsyncGenerator[Tuple[str, dict], None]:
        async for event in aiosseclient(
            urljoin(self.base_url, "events"), auth=self.auth
        ):
            yield event.event, json.loads(event.data)

    # TODO: Convert json to dto
    async def get_topology(self) -> Optional[dict]:
        async with aiohttp.ClientSession(auth=self.auth) as session:
            async with session.get(urljoin(self.base_url, "topology")) as resp:
                if resp.status == HTTPStatus.OK.value:
                    return await resp.json()
                else:
                    self.logger.error("Update topology error:", resp.status)
                    self.logger.error("Update topology error:", await resp.text())
                    return None
