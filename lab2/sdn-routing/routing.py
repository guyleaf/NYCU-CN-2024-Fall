import asyncio
from asyncio import AbstractEventLoop, tasks
import itertools
from rich import print
import matplotlib.pyplot as plt
import networkx as nx

from api import API

_SHELL_LAYOUT = [
    ["of:0000000000000002"],
    [f"of:000000000000000{i}" for i in [6, 1, 3, 4, 8, 5, 7]],
]


def _cancel_all_tasks(event_loop: AbstractEventLoop):
    f"""Reference from {asyncio.runners._cancel_all_tasks}#54 in {asyncio.runners}"""
    to_cancel = tasks.all_tasks(event_loop)
    if not to_cancel:
        return

    for task in to_cancel:
        task.cancel()

    event_loop.run_until_complete(
        tasks.gather(*to_cancel, loop=event_loop, return_exceptions=True)
    )

    for task in to_cancel:
        if task.cancelled():
            continue
        if task.exception() is not None:
            event_loop.call_exception_handler(
                {
                    "message": "unhandled exception during shutdown",
                    "exception": task.exception(),
                    "task": task,
                }
            )


# NOTE: non thread-safe implementation
class RoutingVNF:
    def __init__(self, api_client: API, debug: bool = False):
        self.api_client = api_client
        self.net = nx.DiGraph()

        self.handlers = dict(
            link=self._handle_link_event,
            device=self._handle_device_event,
            host=self._handle_host_event,
        )
        self.print = print if debug else lambda *args, **kwargs: None
        self.debug = debug

    def _handle_link_event(self, data: dict):
        event_type = data["type"]
        subject = data["subject"]
        link_type = subject["type"]
        link_state = subject["state"]

        if link_type != "DIRECT":
            return

        if event_type == "LINK_ADDED":
            if link_state == "ACTIVE":
                self._add_link(subject)
        elif event_type == "LINK_UPDATED":
            if link_state == "ACTIVE":
                self._add_link(subject)
            elif link_state == "INACTIVE":
                self._remove_link(subject)
        elif event_type == "LINK_REMOVED":
            self._remove_link(subject)

    def _handle_device_event(self, data: dict):
        pass

    def _handle_host_event(self, data: dict):
        event_type = data["type"]
        host = data["subject"]

        if event_type == "HOST_ADDED":
            self._add_host(host)
        elif event_type == "HOST_MOVED":
            prev_host = data["prevSubject"]
            self._remove_host(prev_host)
            self._add_host(host)
        elif event_type == "HOST_REMOVED":
            self._remove_host(host)

    def _add_link(self, link: dict):
        src = link["src"]["id"]
        dst = link["dst"]["id"]
        port = link["src"]["port"]

        self.net.add_edge(src, dst, port=port)
        self.print("Data:", src, dst, port)
        self.print("Link 1 (Add):", src, dst, self.net[src][dst]["port"])
        if dst in self.net and src in self.net[dst]:
            self.print("Link 2 (Add):", dst, src, self.net[dst][src]["port"])

    def _remove_link(self, link: dict):
        src = link["src"]["id"]
        dst = link["dst"]["id"]
        if src in self.net:
            self.print("Link (Remove):", src, dst, self.net[src][dst]["port"])
            self.net.remove_edge(src, dst)

    def _add_host(self, host: dict):
        host_id = host["id"]

        # the connection point of the connected switch
        location = host["location"]
        switch_id = location["id"]
        switch_port = location["port"]

        self.net.add_node(host_id)
        self.net.add_edge(host_id, switch_id)
        self.net.add_edge(switch_id, host_id, port=switch_port)

        self.print("Host (Add):", host_id)

    def _remove_host(self, host: dict):
        host_id = host["id"]
        # removing node will also remove all connected edges
        if host_id in self.net:
            self.net.remove_node(host_id)
        self.print("Host (Remove):", host_id)

    async def _listen_events(self, queue: asyncio.Queue):
        async for event, data in self.api_client.listen_events():
            queue.put_nowait((event, data))

    async def _consume_events(self, queue: asyncio.Queue):
        while True:
            event, data = await queue.get()
            handler = self.handlers.get(event)
            if handler is not None:
                handler(data)
            else:
                raise NotImplementedError(f"Receive unknown event, {event}.")
            queue.task_done()

    async def _update_topology(self):
        # wait at least 1s for event listener
        await asyncio.sleep(1)

        topology = await self.api_client.get_topology()

        # Add switches
        # NOTE: we consider every device is a switch.
        switches = (device["id"] for device in topology["devices"])
        self.net.add_nodes_from(switches)

        # Add hosts
        for host in topology["hosts"]:
            self._add_host(host)

        # Add links
        # one connection has bidirectional links
        # so, we only save the source port
        links = (
            (link["src"]["id"], link["dst"]["id"], dict(port=link["src"]["port"]))
            for link in topology["links"]
            if link["type"] == "DIRECT" and link["state"] == "ACTIVE"
        )
        self.net.add_edges_from(links)

    def visualize(self):
        hosts = []
        for node in itertools.chain.from_iterable(_SHELL_LAYOUT):
            for neighbor in self.net[node].keys():
                if neighbor.count(":") > 1:
                    hosts.append(neighbor)

        nx.draw_shell(
            self.net,
            nlist=_SHELL_LAYOUT + [hosts],
            with_labels=True,
            font_size=5,
            font_weight="bold",
            node_size=250,
            style="--",
        )
        plt.savefig("graph.png", bbox_inches="tight", dpi=150)

    def run(self):
        event_loop = asyncio.get_event_loop()
        event_queue = asyncio.Queue(loop=event_loop)

        try:
            # listen events eagerly to avoid race condition during loading topology
            event_loop.create_task(self._listen_events(event_queue))

            # load topology
            print("Loading topology...")
            event_loop.run_until_complete(self._update_topology())
            print("Loaded topology successfully.")

            # consume events
            print("Listening on events... Hit '<ctrl-c>' to exit.")
            event_loop.run_until_complete(self._consume_events(event_queue))
        finally:
            if self.debug:
                print("Taking a graph snapshot...")
                try:
                    self.visualize()
                except Exception:
                    pass

            print("Stopping all tasks...")
            try:
                _cancel_all_tasks(event_loop)
                event_loop.run_until_complete(event_loop.shutdown_asyncgens())
            finally:
                event_loop.close()
