from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
from processing import HumanSwarmConsensusMicroservice

# Initialize app and service
app = FastAPI()
microservice = HumanSwarmConsensusMicroservice()

# --------------------
# Request Models
# --------------------
class SliderRequest(BaseModel):
    discussionId: int
    submissions: List[List[float]]
    mode: str

class ResetRequest(BaseModel):
    discussionId: int

# --------------------
# Endpoints
# --------------------
@app.post("/process_slider")
def process_slider_endpoint(request: SliderRequest):
    result = microservice.process_batch(
        discussion_id=request.discussionId,
        submissions=request.submissions,
        mode=request.mode
    )
    return result


@app.post("/reset_discussion_state")
def reset_discussion_endpoint(request: ResetRequest):
    return microservice.reset_discussion(request.discussionId)


@app.get("/health")
def health():
    return {"status": "ok"}
