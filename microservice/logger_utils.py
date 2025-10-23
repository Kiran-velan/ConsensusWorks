# logger_utils.py
import logging
import os

LOG_DIR = os.path.join(os.path.dirname(__file__), "Logs")
os.makedirs(LOG_DIR, exist_ok=True)

def get_discussion_logger(discussion_id: int) -> logging.Logger:
    """
    Returns a dedicated debug logger for a specific discussion.
    Each discussion gets its own log file.
    """
    logger_name = f"discussion_{discussion_id}"
    logger = logging.getLogger(logger_name)

    # Avoid duplicate handlers
    if not logger.handlers:
        logger.setLevel(logging.DEBUG)

        log_file = os.path.join(LOG_DIR, f"discussion_{discussion_id}.log")
        handler = logging.FileHandler(log_file, encoding='utf-8')
        handler.setLevel(logging.DEBUG)

        formatter = logging.Formatter("%(message)s")
        handler.setFormatter(formatter)

        logger.addHandler(handler)
        logger.propagate = False  # Don’t send to root logger

    return logger
