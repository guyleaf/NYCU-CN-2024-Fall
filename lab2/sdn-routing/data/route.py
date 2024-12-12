import dataclasses
from dataclasses import dataclass
import operator
from typing import List, Optional

from networkx import Graph

from data.connect_point import ConnectPoint


@dataclass(frozen=True)
class Route:
    points: List[ConnectPoint]
    id: Optional[str] = None

    @property
    def src(self):
        return self.points[0]

    @property
    def dst(self):
        return self.points[-1]

    @property
    def path(self):
        return [self.points[0]] + self.points[1:-1:2] + [self.points[-1]]

    @property
    def path_ids(self):
        return [node.id for node in self.path]

    @property
    def devices(self):
        # single device should only use id (port is for point)
        return [
            dataclasses.replace(device, port=None) for device in self.points[1:-1:2]
        ]

    def as_dict(self) -> dict:
        return dataclasses.asdict(self)

    @classmethod
    def from_api(cls, data: dict):
        points = [
            ConnectPoint(id=point["id"], port=point["port"]) for point in data["points"]
        ]
        device_points = points[1:-1]
        for point1, point2 in zip(device_points[:-1:2], device_points[1::2]):
            assert (
                point1.id == point2.id
            ), "Each pair of adjacent points should be on the same device."
        return cls(points, id=data["id"])

    @classmethod
    def from_path(cls, net: Graph, path: List[str], id: Optional[str] = None):
        assert isinstance(net, Graph)
        assert len(path) >= 2

        # construct points
        points = []
        for node1, node2 in zip(path[:-1], path[1:]):
            data = net.get_edge_data(node1, node2)

            # FIXME: maybe we should accept points instead of path
            points.append(ConnectPoint(id=node1, port=data["src_port"]))
            points.append(ConnectPoint(id=node2, port=data["dst_port"]))
        return cls(points, id=id)
