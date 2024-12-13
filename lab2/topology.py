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


class Lab2Topo(Topo):
    def build(self):
        # create 8 switches
        s1, s2, s3, s4, s5, s6, s7, s8 = (self.addSwitch(f"s{i}") for i in range(1, 9))

        # create 9 hosts
        h1, h2, h3, h4, h5, h6, h7, h8, h9 = (
            self.addHost(f"H{i}") for i in range(1, 10)
        )

        # follow the lab's graph to link
        # connect hosts
        self.addLink(s1, h1)
        self.addLink(s3, h2)
        self.addLink(s4, h9)
        self.addLink(s5, h4)
        self.addLink(s5, h5)
        self.addLink(s6, h8)
        self.addLink(s7, h3)
        self.addLink(s8, h6)
        self.addLink(s8, h7)

        # interconnection
        self.addLink(s1, s2)
        self.addLink(s1, s3)
        self.addLink(s1, s6)
        self.addLink(s2, s3)
        self.addLink(s2, s4)
        self.addLink(s2, s5)
        self.addLink(s2, s7)
        self.addLink(s3, s4)
        self.addLink(s4, s5)
        self.addLink(s4, s8)
        self.addLink(s5, s7)
        self.addLink(s5, s8)
        self.addLink(s6, s7)


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
    topo = Lab2Topo()
    controller = partial(RemoteController, ip=args.ip, port=args.port)
    switch = partial(OVSSwitch, protocols=args.openflow_version)
    net = Mininet(
        topo=topo,
        controller=controller,
        switch=switch,
        xterms=args.debug,
        waitConnected=True,
        # autoStaticArp=True,
        # fix mac  for keeping the intents or rules
        # autoSetMaddressesacs=True,
    )

    # run tests
    net.start()

    print(
        "Pinging the hosts to let the controller know... Please wait for a few seconds"
    )
    net.pingAll(timeout=0.01)
    net.pingAll()

    if args.debug:
        CLI(net)

    net.stop()
