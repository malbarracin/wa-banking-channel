# syntax=docker/dockerfile:1.7
FROM ghcr.io/astral-sh/uv:0.13.9 AS uv

FROM python:3.12-slim AS builder
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=0
WORKDIR /app
COPY pyproject.toml uv.lock README.md ./
COPY src ./src
COPY migrations ./migrations
RUN uv sync --frozen --no-dev --no-editable

FROM python:3.12-slim AS runtime
ENV PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH" \
    KB_PORT=8102
WORKDIR /app

RUN groupadd --system --gid 10001 kb \
    && useradd --system --uid 10001 --gid kb --home-dir /app --shell /usr/sbin/nologin kb

COPY --from=builder /app/.venv /app/.venv
COPY pyproject.toml uv.lock README.md ./
COPY src ./src
COPY migrations ./migrations
COPY config ./config
COPY content ./content
COPY docker/entrypoint.sh docker/healthcheck.py /app/docker/

RUN chmod +x /app/docker/entrypoint.sh \
    && chown -R kb:kb /app

USER kb
EXPOSE 8102
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD ["python", "/app/docker/healthcheck.py"]

ENTRYPOINT ["/app/docker/entrypoint.sh"]
CMD ["api"]
