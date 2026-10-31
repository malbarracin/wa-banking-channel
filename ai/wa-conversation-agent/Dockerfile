FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.13.6 /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock README.md ./
COPY src ./src
RUN uv sync --frozen --no-dev
USER 10001:10001
EXPOSE 8103
CMD [".venv/bin/python", "-c", "import wa_conversation_agent; print('H9: base importable; servidor pendiente')"]
