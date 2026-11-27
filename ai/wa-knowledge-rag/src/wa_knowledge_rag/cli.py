"""Knowledge base command-line entry point."""

from argparse import ArgumentParser


def main() -> None:
    parser = ArgumentParser(prog="kb", description="Base de conocimiento del canal WhatsApp")
    parser.parse_args()
