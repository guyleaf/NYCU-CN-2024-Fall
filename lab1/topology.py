import argparse
import sys
from functools import partial

from mininet.clean import cleanup
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import OVSSwitch, RemoteController
from mininet.topo import Topo
from mininet.util import dumpNodeConnections


def parse_args():
    parser = argparse.ArgumentParser(
        description="Run the mininet with lab1 topology.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--ip",
        type=str,
        default="127.0.0.1",
        help="The IP address of the remote controller.",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=6653,
        help="The port of the remote controller.",
    )
    parser.add_argument(
        "--openflow-version",
        type=str,
        default="OpenFlow13",
        help="The version of the openflow protocol to communicate between switch and controller.",
    )
    parser.add_argument(
        "--debug",
        action="store_true",
        help="Run in debug mode.",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Show verbose logs.",
    )
    args = parser.parse_args()

    return args


class Lab1Topo(Topo):
    def build(self):
        # create 3 switches
        s1, s2, s3 = (self.addSwitch(f"s{i}") for i in range(1, 4))

        # create 4 hosts
        a, b, c, d = (self.addHost(chr(ord("A") + i)) for i in range(4))

        # follow the lab's graph to link
        # specify port numbers explicitly for better understanding
        self.addLink(s1, b, port1=3)
        self.addLink(s2, d, port1=2)
        self.addLink(s2, c, port1=3)
        self.addLink(s3, a, port1=1)

        # NOTE: default controller doesn't support loop
        # reference: https://github.com/mininet/mininet/wiki/Introduction-to-Mininet#multipath-routing
        self.addLink(s1, s3, port1=1, port2=3)
        self.addLink(s1, s2, port1=2, port2=4)
        self.addLink(s2, s3, port1=1, port2=2)


if __name__ == "__main__":
    assert sys.version_info >= (3, 8), "The python version should be >= 3.8."
    args = parse_args()

    # clean up the previous setup
    cleanup()

    if args.verbose:
        setLogLevel("debug")
    else:
        setLogLevel("info")

    # construct network
    topo = Lab1Topo()
    controller = partial(RemoteController, ip=args.ip, port=args.port)
    switch = partial(
        OVSSwitch, protocols=args.openflow_version, failMode="standalone", stp=True
    )
    net = Mininet(
        topo=topo,
        controller=controller,
        switch=switch,
        xterms=args.debug,
        waitConnected=True,
        # fix mac addresses for keeping the intents or rules
        # autoSetMacs=True,
    )

    # run tests
    net.start()
    if args.debug:
        CLI(net)
    else:
        dumpNodeConnections(net.hosts)
        net.pingAll()
    net.stop()
