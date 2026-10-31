"""Agente H9 con instrucción versionada y sin conexiones al importar."""

from importlib.resources import files

from google.adk.agents import Agent

from .config import Settings
from .knowledge import consultar_conocimiento
from .mcp_tools import before_tool_callback, create_toolset

root_agent = Agent(
    name="wa_conversation_agent",
    model=Settings().model,
    instruction=files("wa_conversation_agent").joinpath("prompts/instruccion_v1.md").read_text(encoding="utf-8"),
    tools=[consultar_conocimiento, create_toolset(Settings())],
    before_tool_callback=before_tool_callback,
)
