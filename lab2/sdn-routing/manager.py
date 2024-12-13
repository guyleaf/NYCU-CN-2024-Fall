import asyncio
import dataclasses
import functools
from collections import defaultdict
from copy import deepcopy
from enum import Flag, auto
from itertools import combinations, repeat
from typing import Dict, List, Optional, Set, Tuple

import networkx as nx
from rich import print

from api import API
from data.connect_point import ConnectPoint
from data.route import Route


class Action(Flag):
    CREATE = auto()
    DELETE = auto()


def debounce(delay: float):
    def decorator(func):
        event_loop = asyncio.get_event_loop()

        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            wrapper.func.cancel()
            wrapper.func = event_loop.call_later(
                delay, functools.partial(func, **kwargs), args
            )

        wrapper.func = event_loop.call_later(delay, lambda: None)
        return wrapper

    return decorator


# functions
# 1. manage routes
# 2. keep states consistent
# 3.
# TODO: Refactor this with custom data structure
class RouteManager:
    def __init__(self, api_client: API):
        self.is_setup = False
        self.api_client = api_client

        # (src, dst): Optional[list]
        self.routes: Dict[ConnectPoint, Dict[ConnectPoint, Optional[Route]]] = dict()
        # (src link, dst link): set((src, dst), ...)
        self.link_to_routes: Dict[
            ConnectPoint,
            Dict[ConnectPoint, Set[Tuple[ConnectPoint, ConnectPoint]]],
        ] = defaultdict(lambda: defaultdict(set))

        # for pending to send the update request to the controller
        # data format: (route, removed or not)
        self.route_queue = None
        self.action_handlers = {
            Action.CREATE: self._add_batch_routes,
            Action.DELETE: self._remove_batch_routes,
        }

    @property
    def missings(self):
        for src, src_routes in self.routes.items():
            for dst, route in src_routes.items():
                if route is None:
                    yield src, dst

    def _pre_check(self):
        if not self.is_setup:
            raise RuntimeError("Please run the setup method first!")

    async def _add_batch_routes(self, routes: List[Route]):
        if len(routes) == 0:
            return

        print("Route (Add):", len(routes))

        # remote request
        content = [route.as_dict() for route in routes]
        raw_routes = await self.api_client.add_routes(content)

        # update local table
        for route, raw_route in zip(routes, raw_routes):
            route = dataclasses.replace(route, id=raw_route["id"])
            # devices = route.devices

            # critical
            self.routes[route.src][route.dst] = route
            # for src_device, dst_device in zip(devices[:-1], devices[1:]):
            #     self.link_to_routes[src_device][dst_device].add((route.src, route.dst))

    async def _remove_batch_routes(self, routes: List[Route]):
        if len(routes) == 0:
            return

        print("Route (Remove):", len(routes), "with possible duplicates")

        # remote request
        content = [route.as_dict() for route in routes]
        await self.api_client.delete_routes(content)

        # update local table
        for route in routes:
            devices = route.devices
            # critical
            for src_device, dst_device in zip(devices[:-1], devices[1:]):
                self.link_to_routes[src_device][dst_device].discard(
                    (route.src, route.dst)
                )
            if route.src in self.routes and route.dst in self.routes[route.src]:
                self.routes[route.src][route.dst] = None

    async def _consume_route_changes(self):
        prev_action = Action.CREATE
        routes = []

        # execute batch actions in these conditions
        # 1. same consecutive actions
        # 2. queue is empty
        while True:
            route, action, callback = await self.route_queue.get()

            # execute batch actions if 1.
            if prev_action != action:
                func = self.action_handlers[prev_action]
                await func(routes)
                routes = []

            routes.append(route)

            self.route_queue.task_done()
            if self.route_queue.empty():
                func = self.action_handlers[action]
                await func(routes)
                routes = []

            if callable(callback):
                callback()

            prev_action = action

    def _find_path(self, net: nx.DiGraph, src: str, dst: str) -> Optional[List[str]]:
        assert isinstance(src, str) and isinstance(dst, str)
        try:
            return nx.shortest_path(net, src, dst)
        except nx.NetworkXNoPath:
            return None

    def _add_route(self, net: nx.DiGraph, src: ConnectPoint, dst: ConnectPoint) -> bool:
        if self.routes[src][dst] is not None:
            raise RuntimeError(f"The route between ({src}, {dst}) already exists.")

        # find the shortest path
        path = self._find_path(net, src.id, dst.id)
        if path is None:
            return False

        # create a route based on the path
        route = Route.from_path(net, path)

        # add the route to local table
        # route id will be updated soon
        devices = route.devices
        self.routes[route.src][route.dst] = route
        for src_device, dst_device in zip(devices[:-1], devices[1:]):
            self.link_to_routes[src_device][dst_device].add((route.src, route.dst))

        # add a route
        self.route_queue.put_nowait((route, Action.CREATE, None))
        return True

    def _remove_route(
        self,
        net: nx.DiGraph,
        src: ConnectPoint,
        dst: ConnectPoint,
        update_after_remove: bool = True,
    ) -> bool:
        def __callback():
            self.update_missing_routes(net)

        if src in self.routes and dst in self.routes[src]:
            route = self.routes[src][dst]
        else:
            return False

        if route is None:
            return False

        callback = None
        if update_after_remove:
            callback = __callback

        # delete route
        self.route_queue.put_nowait((route, Action.DELETE, callback))
        return True

    def _remove_link(
        self, net: nx.DiGraph, src_device: ConnectPoint, dst_device: ConnectPoint
    ) -> bool:
        success = True
        host_pairs = deepcopy(self.link_to_routes[src_device][dst_device])

        # critical
        for src, dst in host_pairs:
            success = self._remove_route(net, src, dst) and success

        return success

    def update_missing_routes(self, net: nx.DiGraph):
        self._pre_check()
        for src, dst in self.missings:
            self._add_route(net, src, dst)

    def remove_link(
        self, net: nx.DiGraph, src_device_id: str, dst_device_id: str
    ) -> bool:
        self._pre_check()
        src_device = ConnectPoint(src_device_id)
        dst_device = ConnectPoint(dst_device_id)
        success = self._remove_link(net, src_device, dst_device)
        # self.update_missing_routes(net)
        return success

    def remove_device(self, net: nx.DiGraph, device_id: str) -> bool:
        self._pre_check()
        success = True
        device = ConnectPoint(device_id)
        for dst_device in list(self.link_to_routes[device].keys()):
            success = self._remove_link(net, device, dst_device) and success
            success = self._remove_link(net, dst_device, device) and success
        # self.update_missing_routes(net)
        return success

    def add_host(self, net: nx.DiGraph, host_id: str) -> bool:
        self._pre_check()
        host = ConnectPoint(host_id)
        if host in self.routes:
            return False

        # create pairs
        host_routes = {}
        for host, other_host in zip(repeat(host), self.routes.keys()):
            host_routes.setdefault(other_host)
            self.routes[other_host].setdefault(host)
        self.routes[host] = host_routes

        self.update_missing_routes(net)
        return True

    def remove_host(self, net: nx.DiGraph, host_id: str) -> bool:
        self._pre_check()
        success = True
        host = ConnectPoint(host_id)
        for other_host in list(self.routes[host].keys()):
            success = (
                self._remove_route(net, host, other_host, update_after_remove=False)
                and success
            )
            success = (
                self._remove_route(net, other_host, host, update_after_remove=False)
                and success
            )
            del self.routes[other_host][host]
        del self.routes[host]
        return success

    async def setup(self, net: nx.DiGraph, host_ids: List[str]):
        event_loop = asyncio.get_event_loop()
        self.route_queue = asyncio.Queue(loop=event_loop)

        # if there is no host in network, clear all routes for consistence
        if len(host_ids) == 0:
            await self.api_client.clear_routes()

        # create pairs
        hosts = [ConnectPoint(host_id) for host_id in host_ids]
        for src, dst in combinations(hosts, 2):
            if src == dst:
                continue

            self.routes.setdefault(src, {})
            self.routes[src].setdefault(dst)
            self.routes.setdefault(dst, {})
            self.routes[dst].setdefault(src)

        # fetch routes
        raw_routes = await self.api_client.get_routes()
        routes = [Route.from_api(raw_route) for raw_route in raw_routes]

        for route in routes:
            src, dst = route.src, route.dst
            devices = route.devices

            # TODO: don't store and delete directly if it is not a path
            # store in route table
            self.routes.setdefault(src, {})
            self.routes[src].setdefault(dst)
            self.routes[src][dst] = route
            for src_device, dst_device in zip(devices[:-1], devices[1:]):
                self.link_to_routes[src_device][dst_device].add((src, dst))

            # sync with topology
            if not nx.is_simple_path(net, route.path_ids):
                self._remove_route(net, src, dst, update_after_remove=False)
                del self.routes[src]

        self.is_setup = True
        self.update_missing_routes(net)

    async def run(self):
        self._pre_check()

        # TODO: consume route changes
        await self._consume_route_changes()
