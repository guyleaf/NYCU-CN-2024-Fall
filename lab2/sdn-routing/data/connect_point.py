from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class ConnectPoint:
    id: str
    port: Optional[str] = None
