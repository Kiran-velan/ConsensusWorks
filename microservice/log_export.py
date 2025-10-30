from fastapi import APIRouter, HTTPException
from fastapi.responses import Response
import zipfile
import io
import os
from datetime import datetime

router = APIRouter()

@router.get("/api/export/logs/all")
async def export_all_logs():
    """
    Export all FastAPI logs as a ZIP file
    """
    logs_dir = "/app/logs/Logs"
    return await export_logs_directory(logs_dir, "fastapi-all-logs.zip")

@router.get("/api/export/logs/by-date")
async def export_logs_by_date():
    """
    Export logs with date in filename
    """
    logs_dir = "/app/logs/Logs"
    date_str = datetime.now().strftime("%Y-%m-%d")
    filename = f"fastapi-logs-{date_str}.zip"
    return await export_logs_directory(logs_dir, filename)

@router.get("/api/export/logs/list")
async def list_log_files():
    """
    List all available log files
    """
    logs_dir = "/app/logs/Logs"

    if not os.path.exists(logs_dir):
        return {"message": "Logs directory does not exist", "files": []}

    try:
        files = []
        for item in os.listdir(logs_dir):
            item_path = os.path.join(logs_dir, item)
            if os.path.isfile(item_path):
                file_stat = os.stat(item_path)
                files.append({
                    "name": item,
                    "size": file_stat.st_size,
                    "modified": datetime.fromtimestamp(file_stat.st_mtime).isoformat()
                })

        return {
            "directory": logs_dir,
            "total_files": len(files),
            "files": sorted(files, key=lambda x: x["modified"], reverse=True)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error listing logs: {str(e)}")

async def export_logs_directory(logs_dir: str, filename: str):
    """
    Helper function to export a directory as ZIP
    """
    if not os.path.exists(logs_dir):
        raise HTTPException(status_code=404, detail="Logs directory not found")

    # Check if directory is empty
    if not os.listdir(logs_dir):
        raise HTTPException(status_code=404, detail="Logs directory is empty")

    try:
        # Create zip in memory
        zip_buffer = io.BytesIO()
        with zipfile.ZipFile(zip_buffer, "w", zipfile.ZIP_DEFLATED) as zip_file:
            for root, dirs, files in os.walk(logs_dir):
                for file in files:
                    file_path = os.path.join(root, file)
                    # Create relative path for zip
                    arcname = os.path.relpath(file_path, logs_dir)
                    zip_file.write(file_path, arcname)

        zip_buffer.seek(0)
        zip_data = zip_buffer.getvalue()

        if len(zip_data) == 0:
            raise HTTPException(status_code=404, detail="No log files found to export")

        return Response(
            content=zip_data,
            media_type="application/zip",
            headers={
                "Content-Disposition": f"attachment; filename={filename}",
                "Content-Length": str(len(zip_data))
            }
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error creating export: {str(e)}")