"""Agente H9 con instrucción versionada y sin conexiones al importar."""

from importlib.resources import files

from google.adk.agents import Agent

from .config import Settings

root_agent = Agent(
    name="wa_conversation_agent",
    model=Settings().model,
    instruction=files("wa_conversation_agent").joinpath("prompts/instruccion_v1.md").read_text(encoding="utf-8"),
    tools=[],
)
