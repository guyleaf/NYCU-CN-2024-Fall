import argparse
import sys
import time

from rich import print

from api import API
from routing import RoutingVNF


def parse_args():
    parser = argparse.ArgumentParser(
        description="A routing VNF for SDN",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--base-url",
        help="Base url of the controller API",
        default="http://172.18.0.2:8181/onos/sdn-routing-rest/",
    )
    parser.add_argument(
        "--username",
        help="Login username for the controller API",
        default="onos",
    )
    parser.add_argument(
        "--password",
        help="Login password for the controller API",
        default="rocks",
    )
    parser.add_argument("--debug", action="store_true", help="Enable debug mode.")
    args = parser.parse_args()

    return args


if __name__ == "__main__":
    assert sys.version_info >= (3, 8), "The python version should be >= 3.8."
    args = vars(parse_args())
    debug = args.pop("debug")

    api_client = API(**args)
    routing_vnf = RoutingVNF(api_client, debug=debug)

    while True:
        try:
            routing_vnf.run()
        except Exception:
            print("[yellow bold]Trying to reconnect...")
            time.sleep(3)
