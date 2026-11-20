# Windows desktop controller

Run from the repository root with Python 3.11:

```bat
py -3.11 -m venv desktop\.venv
desktop\.venv\Scripts\python -m pip install -r desktop\requirements.txt
desktop\.venv\Scripts\python desktop\app.py
```

Press P to pause/resume and Q or Esc to quit. Edit `gesture-map.json` to customise the gesture actions. Camera frames are processed locally.