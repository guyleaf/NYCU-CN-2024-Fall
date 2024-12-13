import json
import logging
from typing import AsyncGenerator, List, Tuple
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
            urljoin(self.base_url, "events"), auth=self.auth, raise_for_status=True
        ):
            yield event.event, json.loads(event.data)

    # TODO: Convert json to dto
    async def get_topology(self) -> dict:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.get(urljoin(self.base_url, "topology")) as resp:
                return await resp.json()

    async def get_routes(self) -> List[dict]:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.get(urljoin(self.base_url, "routes")) as resp:
                return await resp.json()

    async def add_routes(self, routes: List[dict]) -> List[dict]:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.post(
                urljoin(self.base_url, "routes"), json=routes
            ) as resp:
                return await resp.json()

    async def update_routes(self, routes: List[dict]) -> None:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.put(
                urljoin(self.base_url, "routes"), json=routes
            ) as resp:
                pass

    async def delete_routes(self, routes: List[dict]) -> None:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.delete(
                urljoin(self.base_url, "routes"), json=routes
            ) as resp:
                pass

    async def clear_routes(self) -> None:
        async with aiohttp.ClientSession(
            auth=self.auth, raise_for_status=True
        ) as session:
            async with session.post(
                urljoin(self.base_url, "routes/reset"), data=""
            ) as resp:
                pass
