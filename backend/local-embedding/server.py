from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import torch
import uvicorn
import yaml
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


BACKEND_DIR = Path(__file__).resolve().parents[1]
PROJECT_ROOT = BACKEND_DIR.parent
APPLICATION_YML = BACKEND_DIR / "src/main/resources/application.yml"


def read_settings() -> dict[str, Any]:
    with APPLICATION_YML.open(encoding="utf-8") as stream:
        config = yaml.safe_load(stream)
    return config.get("local-embedding", {})


SETTINGS = read_settings()
MODEL_PATH = Path(SETTINGS.get("model-path", "models/bge-m3")).expanduser()
if not MODEL_PATH.is_absolute():
    MODEL_PATH = PROJECT_ROOT / MODEL_PATH
MODEL_PATH = MODEL_PATH.resolve()
DIMENSIONS = int(SETTINGS.get("dimensions", 1024))
BASE_URL = str(SETTINGS.get("base-url", "http://127.0.0.1:8090"))


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=32)


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not MODEL_PATH.is_dir():
        raise RuntimeError(f"BGE-M3 model directory not found: {MODEL_PATH}")

    device_setting = str(SETTINGS.get("device", "auto")).lower()
    if device_setting == "auto":
        device = "mps" if torch.backends.mps.is_available() else "cpu"
    else:
        device = device_setting

    try:
        app.state.model = SentenceTransformer(
            str(MODEL_PATH),
            device=device,
            local_files_only=True,
        )
    except Exception:
        if device != "mps":
            raise
        device = "cpu"
        app.state.model = SentenceTransformer(
            str(MODEL_PATH),
            device=device,
            local_files_only=True,
        )

    app.state.device = device
    warmup = app.state.model.encode(
        ["启知社本地向量服务启动"],
        normalize_embeddings=True,
        show_progress_bar=False,
    )
    if len(warmup[0]) != DIMENSIONS:
        raise RuntimeError(
            f"BGE-M3 returned {len(warmup[0])} dimensions, configured for {DIMENSIONS}"
        )
    yield
    del app.state.model


app = FastAPI(title="qi_zhi_she Local BGE-M3 Embeddings", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, Any]:
    model = getattr(app.state, "model", None)
    return {
        "status": "UP" if model is not None else "STARTING",
        "model": "BAAI/bge-m3",
        "dimensions": DIMENSIONS,
        "device": getattr(app.state, "device", None),
    }


@app.post("/embeddings")
def embed(request: EmbeddingRequest) -> dict[str, Any]:
    model = getattr(app.state, "model", None)
    if model is None:
        raise HTTPException(status_code=503, detail="Embedding model is not ready")
    if any(not text.strip() for text in request.texts):
        raise HTTPException(status_code=400, detail="Texts must not be blank")

    vectors = model.encode(
        request.texts,
        batch_size=min(8, len(request.texts)),
        normalize_embeddings=True,
        show_progress_bar=False,
        convert_to_numpy=True,
    )
    return {
        "model": "BAAI/bge-m3",
        "dimensions": DIMENSIONS,
        "embeddings": vectors.tolist(),
    }


if __name__ == "__main__":
    parsed = urlparse(BASE_URL)
    uvicorn.run(app, host=parsed.hostname or "127.0.0.1", port=parsed.port or 8090)
